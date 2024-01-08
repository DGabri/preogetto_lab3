import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.Comparator;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.time.Duration;
import java.util.Iterator;
import com.google.gson.*;
import java.time.Instant;
import java.util.*;
import java.io.*;


public class HotelierServerMain {
    // config variables
    private static final String[] capoluoghi = {"L'Aquila", "Potenza", "Catanzaro", "Napoli", "Bologna", "Trieste", "Roma",
            "Genova", "Milano", "Ancona", "Campobasso", "Torino", "Bari", "Cagliari", "Palermo", "Firenze", "Trento",
            "Perugia", "Aosta", "Venezia" };
    private static final String SERVER_CONFIG = "./assets/server.properties";
    private static int RANKING_REFRESH_RATE;
    private static String MULTICAST_ADDR;
    private static int REVIEW_MIN_DELTA;
    private static int SELECT_TIMEOUT;
    private static int PORT;

    // json files path
    private static final String HOTELS_JSON_PATH = "./assets/Hotels.json";
    private static String USERS_JSON_PATH = "./assets/users.json";
    private static String REVIEWS_JSON_PATH = "./assets/reviews.json";

    // Users tracker, hotels and reviews
    private static Map<String /* username */, Utente> registeredUsers;
    private static Set<String /* username */> loggedInUsers;
    private static Map<String /* citta */, List<Recensione>> reviews;
    private static Map<String /* citta */, List<Hotel>> hotels;
    private static Map<String /* citta */, String /* nomeHotel */> topHotels;

    // object reference to call methods
    private static HotelierServerMain serverRef;

    // multicast variables
    private boolean isFirstCalculation;
    MulticastSocket multicastSocket;
    InetAddress multicastGroup;

    public HotelierServerMain() {

        // load config
        Properties prop = loadConfig(SERVER_CONFIG);
        PORT = Integer.parseInt(prop.getProperty("port"));
        RANKING_REFRESH_RATE = Integer.parseInt(prop.getProperty("rankingRefreshRate"));
        SELECT_TIMEOUT = Integer.parseInt(prop.getProperty("selectTimeout"));
        REVIEW_MIN_DELTA = Integer.parseInt(prop.getProperty("reviewMinDelta"));
        MULTICAST_ADDR = prop.getProperty("multicastAddress");

        // start multicast socket
        try {
            multicastGroup = InetAddress.getByName(MULTICAST_ADDR);
            multicastSocket = new MulticastSocket();

        } catch (IOException e) {
            e.printStackTrace();
        }

        // from seconds calculate ms
        RANKING_REFRESH_RATE *= 1000;
        SELECT_TIMEOUT *= 1000;
        REVIEW_MIN_DELTA *= 1000;

        // Initialize data structures
        registeredUsers = new HashMap<>();
        loggedInUsers = new HashSet<>();
        topHotels = new HashMap<>();
        reviews = new HashMap<>();
        hotels = new HashMap<>();

        // Load data from JSON files
        loadUsersFromJson();
        loadHotelsFromJson();
        loadReviewsFromJson();
    }
    
    public static void main(String[] args) {
        serverRef = new HotelierServerMain();

        // variable to not send multicast hotel change at first startup
        serverRef.isFirstCalculation = true;

        // initialize top hotels hashmap and calculate ranking at startup
        serverRef.initializeTopHotelsHashMap();
        serverRef.recalculateRanking();

        // put start time so after RANKING_REFRESH_RATE i can check if a new ranking is calculated
        long startTime = System.currentTimeMillis();

        // try with resources so it closes everything at shutdown
        try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
                Selector selector = Selector.open()) {

            // set non blocking and bind to port
            serverSocketChannel.bind(new InetSocketAddress(PORT));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.printf("In ascolto su porta: %d\n", PORT);

            while (true) {
                // timeout of SELECT_TIMEOUT seconds
                if (selector.select(SELECT_TIMEOUT) > 0) {

                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iter = selectedKeys.iterator();

                    while (iter.hasNext()) {
                        SelectionKey key = iter.next();

                        // new connection available
                        if (key.isAcceptable()) {
                            serverRef.acceptConnection(key, selector);
                        // new message readeable
                        } else if (key.isReadable()) {
                            serverRef.readMsg(key, selector);
                        }

                        iter.remove();
                    }
                }
                
                // get current timestamp to check condition
                long now = System.currentTimeMillis();
    
                if (now > (startTime + RANKING_REFRESH_RATE)) {
                    // sort hotel rankings based on score
                    serverRef.recalculateRanking();
                        
                    startTime = now;
                }
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }

        serverRef.multicastSocket.close();
    }

    // function to split the received message from the client
    public static String[] splitMessage(String credentials) {
        String[] parts = credentials.split("_");

        return parts;
    }

    // function to load the configuration
    public static Properties loadConfig(String fname) {
        try (FileInputStream fileInputStream = new FileInputStream(fname)) {
            Properties properties = new Properties();
            properties.load(fileInputStream);

            return properties;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // function to accept a new connection
    private void acceptConnection(SelectionKey selKey, Selector selector) {
        try {
            // open serverSocket with channel, and accept connection
            ServerSocketChannel serverSocket = (ServerSocketChannel) selKey.channel();
            SocketChannel socketChannel = serverSocket.accept();
            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_READ);
            
            // attach bytebuffer to save data received
            socketChannel.keyFor(selector).attach(ByteBuffer.allocate(1024));

            System.out.println("Nuovo client connesso: " + socketChannel.getRemoteAddress());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // function to read a message and send response
    public void readMsg(SelectionKey selKey, Selector selector) {
        try {
            SocketChannel socketChannel = (SocketChannel) selKey.channel();
            ByteBuffer lengthBuffer = ByteBuffer.allocate(Integer.BYTES);

            // read message legnth
            int bytesRead = socketChannel.read(lengthBuffer);

            // client closed connection
            if (bytesRead == -1) {
                socketChannel.close();
                return;
            }

            lengthBuffer.flip();
            // read msg length
            int messageLength = lengthBuffer.getInt();

            // read message
            ByteBuffer msgBuffer = ByteBuffer.allocate(messageLength);
            bytesRead = socketChannel.read(msgBuffer);

            if (bytesRead == -1) {
                socketChannel.close();
                System.out.println("Un client ha chiuso la connessione");
                return;
            }

            msgBuffer.flip();
            byte[] data = new byte[msgBuffer.remaining()];
            msgBuffer.get(data);
            String messageReceived = new String(data, StandardCharsets.UTF_8);


            // sen
            String msgToSend = serverRef.handleReceivedMessage(messageReceived);

            // send response message to client
            byte[] responseBytes = msgToSend.getBytes(StandardCharsets.UTF_8);
            ByteBuffer responseBuffer = ByteBuffer.allocate(Integer.BYTES + responseBytes.length);
            responseBuffer.putInt(responseBytes.length);
            responseBuffer.put(responseBytes);
            responseBuffer.flip();

            while (responseBuffer.hasRemaining()) {
                socketChannel.write(responseBuffer);
            }

            // set interest to read next msg
            selKey.interestOps(SelectionKey.OP_READ);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // function to create a string of all matching hotels for a specific city
    public String createHotelsString(String city) {
        List<Hotel> matchingHotels = searchAllHotels(city);

        StringBuilder response = new StringBuilder();

        // append a new hotel on each line so that client can print it
        for (Hotel hotel : matchingHotels) {
            response.append(hotel.toString()).append("\n");
        }

        return response.toString();
    }

    public String handleReceivedMessage(String inputMsg) {
        // take substring of the message
        String msgRcvd = inputMsg.substring(2);

        // take the first character to do operation switch
        switch (inputMsg.substring(0, 1)) {
            /* REGISTER */
            case "1":
                // returns 0 if user is not present, 1 if present
                String rcvdCredentials = msgRcvd;
                String[] credentials = splitMessage(rcvdCredentials);

                String username = credentials[0];
                String password = credentials[1];

                String isRegistered = serverRef.isRegistered(username);

                // user is already registered
                if (isRegistered.equals("1")) {
                    return "-1";
                }
                // need to register user
                else if (isRegistered.equals("0")) {

                    // create new user object and save it to hashmap + users json
                    Utente newUser = new Utente(username, password);
                    return serverRef.registerUser(newUser);
                }

                /* LOGIN */
            case "2":
                // returns 0 if user is not present, 1 if present
                String[] loginCredentials = splitMessage(msgRcvd);

                String usernameLogin = loginCredentials[0];
                String passwordLogin = loginCredentials[1];
                // login
                // returns 1 if login was successful, 0 if not
                return serverRef.login(usernameLogin, passwordLogin);

            /* LOGOUT */
            case "3":
                String[] logoutCredentials = splitMessage(msgRcvd);

                String logoutUsername = logoutCredentials[0];
                return serverRef.logout(logoutUsername);

            /* SEARCH HOTEL */
            case "4":
                // split the received message and return the response
                String[] requestedHotel = splitMessage(msgRcvd);
                String hotelName = requestedHotel[0];
                String city = requestedHotel[1];

                return searchHotel(hotelName, city);

            /* SEARCH ALL HOTELS IN CITY */
            case "5":
                String[] requestedCity = splitMessage(msgRcvd);
                String searchCity = requestedCity[0];

                // function that given the city, searches all hotels and creates a string with
                // matching hotels
                return createHotelsString(searchCity);

            /* INSERT REVIEW */
            case "6":
                String[] review = splitMessage(msgRcvd);

                // get username, hotel name and city
                String reviewerUsername = review[0];
                String reviewedHotelName = review[1];
                String reviewedHotelCity = review[2];

                // get all the scores
                double globalScore = Double.parseDouble(review[3]);
                double position = Double.parseDouble(review[4]);
                double cleaning = Double.parseDouble(review[5]);
                double services = Double.parseDouble(review[6]);
                double quality = Double.parseDouble(review[7]);
                int hotelId = getHotelId(reviewedHotelCity, reviewedHotelName);

                // check if selected city is in capoluoghi
                int isValidCity = isValidCity(reviewedHotelCity);

                // check if the user can write the review or if too little time has passed from last review
                boolean validReview = serverRef.canWriteReview(reviewerUsername, hotelId);

                if ((hotelId != -1) && (isValidCity == 1) && (validReview == true)) {
                    // create new review with the received data
                    Recensione newReview = new Recensione(globalScore, position, cleaning, services, quality,
                            reviewerUsername,
                            hotelId, 1, 0);

                    // update user level
                    serverRef.updateUser(reviewerUsername);

                    // save new data to json
                    addSaveReview(newReview, reviewedHotelCity);
                    
                    // persist utente because written reviews count has increased
                    serverRef.saveUtenteToJson();
                    return "1";
                }

                return "-1";

            /* SHOW BADGES */
            case "7":
                // split credentials and get username
                String[] userCredentials = splitMessage(msgRcvd);
                username = userCredentials[0];

                return serverRef.showBadges(username);

            /* EXIT */
            case "8":
                // close selector and socket channel
                return "8_closed_connection";
        }

        return "-1";
    }

    /* REGISTRATION FUNCTIONS */
    // function to check if username is present in registered users hashmap
    public String isRegistered(String username) {
        // returns 0 if user is not present, 1 if present
        if (registeredUsers.containsKey(username)) {
            return "1";
        }

        return "0";
    }

    public String registerUser(Utente newUser) {
        return addUserAndSaveToJson(newUser);
    }

    /* LOGIN FUNCTIONS */
    // check if user is logged in
    public String loggedInUsersExists(String username) {
        if (loggedInUsers.contains(username)) {
            return "1";
        }

        return "0";
    }

    // insert that user has made a login
    public String loggedInUsersPut(String username) {

        if (!loggedInUsers.contains(username)) {
            loggedInUsers.add(username);
            return "1";
        }

        return "0";
    }

    // returns 1 if login was successful, 0 if not
    public String login(String username, String password) {
        Utente currentUser = registeredUsers.get(username);

        // if null user is not registered
        if ((currentUser != null) && (serverRef.isRegistered(username).equals("1"))) {
            String registerPwd = currentUser.password;

            // get password to see if they match
            if (registerPwd.equals(password)) {
                return "1";
            }

            // passwords don't match
            return "-1";
        }

        return "0";
    }

    // returns 1 if logout was successful, 0 if not
    public String logout(String username) {
        loggedInUsers.remove(username);

        return "1";
    }

    /* SHOW BADGES */
    public String showBadges(String username) {
        Utente utente = registeredUsers.get(username);

        if (utente != null) {
            String userLevel = utente.getUserLevel();
            return userLevel;
        }

        return "-1";
    }

    /* SEARCH FUNCTIONS */
    public int getHotelId(String city, String hotelName) {
        // check if the city is presente in the hotel hashmap
        if (hotels.containsKey(city)) {
            // get hotels for passed city
            List<Hotel> cityHotels = hotels.get(city);

            // scan hotels in city
            for (Hotel hotel : cityHotels) {
                // check match
                if (hotel.name.equals(hotelName)) {
                    // return hotel id
                    return hotel.id;
                }
            }
        }

        // hotel does not exist
        return -1;
    }



    ///////////////////////////
    // UTILITY FUNCTIONS
    ///////////////////////////

    private static String searchHotel(String nomeHotel, String citta) {
        for (List<Hotel> cityHotels : hotels.values()) {
            for (Hotel hotel : cityHotels) {
                if (hotel.name.equalsIgnoreCase(nomeHotel) && hotel.city.equalsIgnoreCase(citta)) {
                    return hotel.toString();
                }
            }
        }
        return "null";
    }

    private static List<Hotel> searchAllHotels(String citta) {
        List<Hotel> matchingHotels = hotels.getOrDefault(citta, new ArrayList<>());

        return matchingHotels;
    }

    public int isValidCity(String city) {
            for (String capoluogo : capoluoghi) {
                if (capoluogo.equalsIgnoreCase(city)) {
                    return 1;
                }
            }
            return 0;
    }


    ///////////////////////////
    // JSON FUNCTIONS
    ///////////////////////////

    /* FUNCTIONS TO LOAD DATA FROM FILE TO CLASS DATA STRUCTURE */

    private JsonObject readJsonFromFile(String filePath) {
        try {
            String jsonData = new String(Files.readAllBytes(Paths.get(filePath)));

            if (jsonData != null && !jsonData.isEmpty()) {
                return JsonParser.parseString(jsonData).getAsJsonObject();
            } else {
                return null;
            }
        } catch (IOException | JsonSyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    /* HOTELS FUNCTIONS */
    private void loadHotelsFromJson() {
        try {
            String hotelsJson = Files.readString(Paths.get(HOTELS_JSON_PATH));
            JsonArray hotelsArray = JsonParser.parseString(hotelsJson).getAsJsonArray();

            for (int i = 0; i < hotelsArray.size(); i++) {
                JsonObject hotelObject = hotelsArray.get(i).getAsJsonObject();
                Hotel hotel = new Gson().fromJson(hotelObject, Hotel.class);

                // Add hotel to the map
                String city = hotel.city;
                hotels.computeIfAbsent(city, k -> new ArrayList<>()).add(hotel);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveHotelsToJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonArray hotelsArray = new JsonArray();

        // Iterate through all cities
        for (Map.Entry<String, List<Hotel>> entry : hotels.entrySet()) {
            List<Hotel> cityHotels = entry.getValue();

            // Iterate through all hotels in the current city
            for (Hotel hotel : cityHotels) {
                JsonObject hotelJson = new JsonObject();
                hotelJson.addProperty("id", hotel.id);
                hotelJson.addProperty("name", hotel.name);
                hotelJson.addProperty("description", hotel.description);
                hotelJson.addProperty("city", hotel.city);
                hotelJson.addProperty("phone", hotel.phone);
                hotelJson.add("services", gson.toJsonTree(hotel.services));
                hotelJson.addProperty("rate", hotel.rate);

                JsonObject ratingsJson = new JsonObject();
                ratingsJson.addProperty("cleaning", hotel.ratings.pulizia);
                ratingsJson.addProperty("position", hotel.ratings.posizione);
                ratingsJson.addProperty("services", hotel.ratings.servizio);
                ratingsJson.addProperty("quality", hotel.ratings.qualita);

                hotelJson.add("ratings", ratingsJson);

                // Add the hotel to the array for the current city
                hotelsArray.add(hotelJson);
            }
        }

        // Write the entire array to the file
        try (FileWriter writer = new FileWriter(HOTELS_JSON_PATH)) {
            gson.toJson(hotelsArray, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* USERS FUNCTIONS */

    private void loadUsersFromJson() {
        registeredUsers = new ConcurrentHashMap<>();
        JsonObject jsonData = readJsonFromFile(USERS_JSON_PATH);

        if (jsonData != null && jsonData.has("utenti")) {
            JsonArray utentiArray = jsonData.getAsJsonArray("utenti");

            for (int i = 0; i < utentiArray.size(); i++) {
                try {
                    Utente utente = new Gson().fromJson(utentiArray.get(i), Utente.class);
                    registeredUsers.put(utente.username, utente);
                } catch (Exception e) {
                    System.err.println("Error loading Utente: " + e.getMessage());
                }
            }
        }
    }

    // function to save all registered users to json
    private void saveUtenteToJson() {
        try {
            JsonObject jsonObject = new JsonObject();
            JsonArray utentiArray = new JsonArray();

            registeredUsers.forEach((key, value) -> utentiArray.add(new Gson().toJsonTree(value)));

            jsonObject.add("utenti", utentiArray);

            try (FileWriter fileWriter = new FileWriter(USERS_JSON_PATH)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(jsonObject, fileWriter);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // function to add a new user to the registered users hashmap and save to json
    public String addUserAndSaveToJson(Utente newUtente) {
        try {
            // get username and use it as key of insertion
            String username = newUtente.username;

            // add username to hashmap
            registeredUsers.put(username, newUtente);

            serverRef.saveUtenteToJson();

            return "1";

        } catch (Exception e) {
            e.printStackTrace();
            return "0";
        }
    }

    public int updateUser(String username) {
        // Check if the username exists in the ConcurrentHashMap
        if (registeredUsers.containsKey(username)) {
            // Retrieve the user object
            Utente currentUser = registeredUsers.get(username);

            // update reviews count
            currentUser.increaseReviewCount();
            // recalculate user level
            currentUser.setUserLevel();

            // Put the updated user back into the map
            registeredUsers.put(username, currentUser);

            return 1;
        }
        return -1;
    }

    /* REVIEWS FUNCTIONS */
    private void loadReviewsFromJson() {
        try {
            String jsonData = new String(Files.readAllBytes(Paths.get(REVIEWS_JSON_PATH)));
            JsonObject reviewsObject = JsonParser.parseString(jsonData).getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : reviewsObject.entrySet()) {
                String hotelIdStr = entry.getKey();
                JsonArray reviewsArray = entry.getValue().getAsJsonArray();
                List<Recensione> reviewsList = new ArrayList<>();

                for (JsonElement reviewElement : reviewsArray) {
                    JsonObject reviewObject = reviewElement.getAsJsonObject();

                    // Extract review details
                    double totale = reviewObject.get("totale").getAsDouble();
                    double posizione = reviewObject.get("posizione").getAsDouble();
                    double pulizia = reviewObject.get("pulizia").getAsDouble();
                    double servizio = reviewObject.get("servizio").getAsDouble();
                    double qualita = reviewObject.get("qualita").getAsDouble();
                    int idHotel = reviewObject.get("idHotel").getAsInt();
                    long ts = reviewObject.get("timestamp").getAsLong();
                    String username = reviewObject.get("username").getAsString();

                    // Create a new Recensione
                    Recensione recensione = new Recensione(totale, posizione, pulizia, servizio, qualita, username,
                            idHotel, 0, ts);

                    // Add the review to the list
                    reviewsList.add(recensione);
                }

                // Add the reviews list to the reviews map
                reviews.put(hotelIdStr, reviewsList);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // function to save to json the review
    private void saveReviewsToJson() {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonObject jsonObject = new JsonObject();

            // Iterate over the ConcurrentHashMap using forEach
            reviews.forEach((hotelId, reviewsList) -> {
                JsonArray reviewsArray = gson.toJsonTree(reviewsList).getAsJsonArray();
                jsonObject.add(hotelId, reviewsArray);
            });

            try (FileWriter fileWriter = new FileWriter(REVIEWS_JSON_PATH)) {
                gson.toJson(jsonObject, fileWriter);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // function to add a review to the list of reviews
    public void addReviewToHotel(String city, Recensione recensione) {

        // Check if the hotel ID is already in the map
        if (reviews.containsKey(city)) {
            // If yes, add the review to the existing list
            reviews.get(city).add(recensione);
        } else {
            // If no, create a new list and add the review
            List<Recensione> reviewList = new ArrayList<>();
            reviewList.add(recensione);
            reviews.put(String.valueOf(city), reviewList);
        }
    }

    // function to add a review
    public void addSaveReview(Recensione recensione, String city) {
        addReviewToHotel(city, recensione);
        saveReviewsToJson();
    }

    /* RANKING FUNCTIONS */

    // function to get the number of reviews per hotel scaled to 100
    public double getReviewsCountPerHotel(List<Recensione> reviewsList) {
        //return ((double) reviewsList.size()) / 10;
        return ((double) reviewsList.size());
    }

    // function to get all reviews for hotel
    public List<Recensione> getReviewsForHotel(List<Recensione> reviews, int hotelId) {
        if (reviews == null) {
            return Collections.emptyList();
        }
        
        List<Recensione> hotelReviews = new ArrayList<>();

        for (Recensione review : reviews) {
            if (review.idHotel == hotelId) {
                hotelReviews.add(review);
            }
        }

        return hotelReviews;
    }

    public double getDeltaDays(long timestamp, long timestampNow) {

        // get seconds diff
        long secondsDuration = Duration.between(Instant.ofEpochMilli(timestamp), Instant.ofEpochMilli(timestampNow))
                .get(ChronoUnit.SECONDS);

        // return days between start and end
        return secondsDuration / (24 * 3600);
    }

    // function to give weigth to a review
    public double getReviewScore(double deltaDays) {
        if ((deltaDays >= 0) && (deltaDays < 5)) {
            return 1;
        }
        else if ((deltaDays >= 5) && (deltaDays < 15)) {
            return 0.8;
        }
        else if ((deltaDays >= 15) && (deltaDays < 30)) {
            return 0.6;
        }
        else if ((deltaDays >= 30) && (deltaDays < 180)) {
            return 0.5;
        }
        else if (deltaDays >= 180) {
            return 0.1;
        }
        
        return 0;
    }
    // function to calculate the delta between last and first review timestamp
    public double getReviewsActuality(List<Recensione> reviewsList) {

        // sort reviewsList by timestamp descending
        reviewsList.sort((r1, r2) -> Long.compare(r2.timestamp, r1.timestamp));

        // sum the total of the weight for each review of one hotel
        long now = System.currentTimeMillis();
        double total = 0;

        for (Recensione review : reviewsList) {
            double deltaDays = serverRef.getDeltaDays(review.timestamp, now);
            double reviewWeight = serverRef.getReviewScore(deltaDays);

            total += reviewWeight;
        }

        return total;
    }

    // function to get reviews quality
    public double getReviewsQualityPerHotel(List<Recensione> reviewsList) {
        // calculate mean score
        int listLength = reviewsList.size();
        int totalScore = 0;
        double meanScore = 0;

        for (Recensione review : reviewsList) {
            totalScore += review.totale;
        }
        
        if (listLength != 0) {
            meanScore = (totalScore / listLength);
            return meanScore;
        }
        
        return 0;
    }

    // function to sort hotels by score, score is calculated using calculateScore()
    public void sortHotelsByScore(List<Recensione> reviewList, List<Hotel> cityHotels) {
        if (reviewList == null || cityHotels == null) {
            // Handle the case where reviews or hotels are null, e.g., return an empty list or throw an exception
            return;
        }

        Map<Integer /* HotelId */, Double /* Score */> scores = new HashMap<>();

        // compute score for every hotel in the list
        for (Recensione review : reviewList) {
            int hotelId = review.idHotel;

            scores.compute(hotelId, (key, previousScore) -> {
                // calculate score for each hotel
                // calculate new score and add it to the previous score
                double newScore = serverRef.calculateScore(hotelId, reviewList);
                double updatedScore = previousScore != null ? previousScore + newScore : newScore;
                return updatedScore;
            });
        }

        // sort descending
        cityHotels.sort(Comparator.comparingDouble((Hotel hotel) -> scores.getOrDefault(hotel.id, 0.0)).reversed());

    }
    
    public void updateHotelGlobalRate() {
        for (String city : hotels.keySet()) {
            // if there is not a review for this city skip the calculation
            if (!reviews.containsKey(city)) {
                break;
            }
    
            // get hotels for this city
            List<Hotel> hotelList = hotels.get(city);
    
            // iterate hotels
            for (Hotel hotel : hotelList) {
                // get reviews for current city and for current hotel
                List<Recensione> cityReviews = reviews.get(city);
                List<Recensione> hotelReviewsList = serverRef.getReviewsForHotel(cityReviews, hotel.id);
    
                // calculate points total
                double cleaningTotal = 0;
                double positionTotal = 0;
                double servicesTotal = 0;
                double qualityTotal = 0;
    
                int numReviews = hotelReviewsList.size();
    
                if (numReviews > 0) {
                    for (Recensione review : hotelReviewsList) {
                        cleaningTotal += review.pulizia;
                        positionTotal += review.posizione;
                        servicesTotal += review.servizio;
                        qualityTotal += review.qualita;
                    }
    
                    // update value with average
                    hotel.ratings.pulizia = cleaningTotal / numReviews;
                    hotel.ratings.posizione = positionTotal / numReviews;
                    hotel.ratings.servizio = servicesTotal / numReviews;
                    hotel.ratings.qualita = qualityTotal / numReviews;
                }
            }
        }
    
        // Persist the update to JSON
        saveHotelsToJson();
    }
    

    // function to sort the ranking of the hotels
    public void recalculateRanking() {
        // first update all the hotels score
        serverRef.updateHotelGlobalRate();

        String newTopHotelName;
        
        // calculate ranking (sort hotels)
        for (String city : hotels.keySet()) {
            List<Hotel> hotelList = hotels.get(city);

            // get oldTopHotelName
            String oldTopHotelName = serverRef.getCurrentTopHotel(city);

            if (hotelList.size() > 0) {
                // get name of actual top hotel

                // sort hotels for each city
                // reviews for specific city
                List<Recensione> cityReviewsList = reviews.get(city);

                serverRef.sortHotelsByScore(cityReviewsList, hotelList);

                // get name of new first hotel
                newTopHotelName = hotelList.get(0).name;
            } else {
                // if there is no hotel
                newTopHotelName = "";
            }

            
            // check if there is a change and oldHotelName is not null (otherwise it is the first ranking)
            if (((newTopHotelName != null) && (oldTopHotelName != null))
            && (!oldTopHotelName.equals(newTopHotelName))) {
                
                //update topHotelName
                serverRef.updateTopHotelName(city, newTopHotelName);

                if (serverRef.isFirstCalculation == false) {
                    String updateMsg = "NEW TOP HOTEL: " + newTopHotelName + " CITY: " + city;
                    // append new hotel to the final string                    
                    serverRef.sendMulticastNotification(updateMsg);
                    System.out.println("SENT NOTIFICATION UPDATE -> " + updateMsg);
                }
            }
        }
        
        serverRef.isFirstCalculation = false;

        // save hotels to json
        serverRef.saveHotelsToJson();
    }

    public void initializeTopHotelsHashMap() {
        for (String city : capoluoghi) {
            serverRef.updateTopHotelName(city, "");
        }
    }

    // given city return top hotel name
    public String getCurrentTopHotel(String city) {
        return topHotels.get(city);
    }

    // given city and topHotelName puts new value
    public void updateTopHotelName(String city, String topHotelName) {
        topHotels.put(city, topHotelName);
    }

    // function called when sorting hotels, it assigns a value to each hotel based
    // on
    // 1. number of reviews
    // 2. reviews actuality
    // 3. reviews quality
    public double calculateScore(int hotelId, List<Recensione> cityReviews) {

        List<Recensione> hotelReviews = serverRef.getReviewsForHotel(cityReviews, hotelId);

        // no reviews present, return 0
        if (hotelReviews.size() == 0) {
            return 0;
        }

        // quantita' recensioni
        double reviewsCount = serverRef.getReviewsCountPerHotel(hotelReviews);

        // attualita' recensioni
        double reviewsActuality = serverRef.getReviewsActuality(hotelReviews);

        // qualita' recensioni
        double reviewsQuality = serverRef.getReviewsQualityPerHotel(hotelReviews);

        return reviewsCount + reviewsActuality + reviewsQuality;
    }
    

    // function to get the last review a user did to a hotel
    // sorted descending so the first is the last review done
    public Recensione getUserReviewHotel(int hotelId, String username) {
        List<Recensione> userReviews = new ArrayList<>();

        // get reviews for user and selected hotel
        for (List<Recensione> reviewsList : reviews.values()) {
            for (Recensione review : reviewsList) {
                if (review.idHotel == hotelId && review.username.equals(username)) {
                    userReviews.add(review);
                }
            }
        }
        
        if (userReviews.size() == 0) {
            return null;
        }

        // sort
        Collections.sort(userReviews, (r1, r2) -> Long.compare(r2.timestamp, r1.timestamp));

        return userReviews.get(0);
    }

    public boolean canWriteReview(String username, int hotelId) {
        Recensione lastReview = serverRef.getUserReviewHotel(hotelId, username);

        if (lastReview != null) {
            long lastReviewTimestamp = lastReview.timestamp;
            long now = System.currentTimeMillis();

            long delta = (now - lastReviewTimestamp) / 1000;

            // check if the time between last review and now is greater than 1 minute
            return (delta > REVIEW_MIN_DELTA);
        }
        
        // if user didn't write any review for this hotel he can write one now
        return true;
    }

    /* MULTICAST FUNCTION */
    public void sendMulticastNotification(String msg) {
        try {
            byte[] buffer = msg.getBytes();
            
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, multicastGroup, PORT + 1);
            serverRef.multicastSocket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
