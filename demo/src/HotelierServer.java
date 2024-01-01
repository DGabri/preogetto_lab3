
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.net.InetSocketAddress;
import java.util.Comparator;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import com.google.gson.*;
import java.util.*;
import java.io.*;



public class HotelierServer {
    // config variables
    private static final String SERVER_CONFIG = "./assets/server.properties";
    private static int RANKING_REFRESH_RATE;
    private static int PORT;

    // json files path
    private static final String HOTELS_JSON_PATH = "./assets/Hotels.json";
    private static String USERS_JSON_PATH = "./assets/users.json";
    private static String REVIEWS_JSON_PATH = "./assets/reviews.json";

    // Users tracker, hotels and reviews
    private static ConcurrentHashMap<String, Utente> registeredUsers;
    private static Set<String> loggedInUsers;
    public static ConcurrentHashMap<String, List<Recensione>> reviews;

    public static ConcurrentHashMap<String, List<Hotel>> hotels;

    // object reference to call methods
    private static HotelierServer serverRef;

    public HotelierServer() {

        // load config
        Properties prop = loadConfig(SERVER_CONFIG);
        PORT = Integer.parseInt(prop.getProperty("port"));
        RANKING_REFRESH_RATE = Integer.parseInt(prop.getProperty("rankingRefreshRate"));

        // Initialize data structures
        registeredUsers = new ConcurrentHashMap<>();
        loggedInUsers = new HashSet<>();
        reviews = new ConcurrentHashMap<>();
        hotels = new ConcurrentHashMap<>();

        // Load data from JSON files
        loadUsersFromJson();
        loadHotelsFromJson();
        loadReviewsFromJson();

        /* 
         
        for (String hotelId : reviews.keySet()) {
            
            List<Recensione> reviewsList = reviews.get(hotelId);
            for (Recensione review : reviewsList) {
                System.out.println(review);
            }
        }
        */
    }

    public static void main(String[] args) {
        serverRef = new HotelierServer();

        try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
                Selector selector = Selector.open()) {

            serverSocketChannel.bind(new InetSocketAddress(PORT));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.printf("[SERVER] Listening on port %d\n", PORT);

            while (true) {
                selector.select();
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();

                    if (key.isAcceptable()) {
                        serverRef.acceptConnection(key, selector);
                    } else if (key.isReadable()) {
                        serverRef.readMsg(key, selector);
                    }

                    iter.remove();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // function to split the received message from the client
    public static String[] splitCredentials(String credentials) {
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

    private void acceptConnection(SelectionKey selKey, Selector selector) {
        try {
            // open serverSocket with channel, and accept connection
            ServerSocketChannel serverSocket = (ServerSocketChannel) selKey.channel();
            SocketChannel socketChannel = serverSocket.accept();
            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_READ);
            // Attach a ByteBuffer to store client data
            socketChannel.keyFor(selector).attach(ByteBuffer.allocate(1024));

            System.out.println("Client connected: " + socketChannel.getRemoteAddress());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void readMsg(SelectionKey selKey, Selector selector) {
        try {
            SocketChannel socketChannel = (SocketChannel) selKey.channel();
            ByteBuffer lengthBuffer = ByteBuffer.allocate(Integer.BYTES);
    
            // Read the length of the message
            int bytesRead = socketChannel.read(lengthBuffer);
    
            if (bytesRead == -1) {
                socketChannel.close();
                System.out.println("Client closed connection");
                return;
            }
    
            lengthBuffer.flip();
            int messageLength = lengthBuffer.getInt();
    
            // Read the actual message
            ByteBuffer msgBuffer = ByteBuffer.allocate(messageLength);
            bytesRead = socketChannel.read(msgBuffer);
    
            if (bytesRead == -1) {
                socketChannel.close();
                System.out.println("Client closed connection");
                return;
            }
    
            msgBuffer.flip();
            byte[] data = new byte[msgBuffer.remaining()];
            msgBuffer.get(data);
            String messageReceived = new String(data, StandardCharsets.UTF_8);
            System.out.println("RECEIVED: " + messageReceived);
    
            // Handle the received message and send back a response
            String msgToSend = serverRef.handleReceivedMessage(messageReceived);
    
            // Echo the message back to the client with length prefix
            byte[] responseBytes = msgToSend.getBytes(StandardCharsets.UTF_8);
            ByteBuffer responseBuffer = ByteBuffer.allocate(Integer.BYTES + responseBytes.length);
            responseBuffer.putInt(responseBytes.length);
            responseBuffer.put(responseBytes);
            responseBuffer.flip();
    
            while (responseBuffer.hasRemaining()) {
                socketChannel.write(responseBuffer);
            }
    
            selKey.interestOps(SelectionKey.OP_READ); // Set interest back to read for the next message
    
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public String createHotelsString(String city) {
        List<Hotel> matchingHotels = searchAllHotels(city);

        StringBuilder response = new StringBuilder();
        for (Hotel hotel : matchingHotels) {
            response.append(hotel.toString()).append("\n");
        }

        System.out.println("---------------------");
        System.out.println(response);
        System.out.println("---------------------");

        // Send the response to the client
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
                String[] credentials = splitCredentials(rcvdCredentials);

                String username = credentials[0];
                String password = credentials[1];

                System.out.println("Username: " + username + " Password: " + password);
                String isRegistered = serverRef.isRegistered(username);

                // user is already registered
                if (isRegistered.equals("1")) {
                    System.out.println("USER ALREADY REGISTERED");
                    return "-1";
                }
                // need to register user
                else if (isRegistered.equals("0")) {

                    // create new user object and save it to hashmap + users json
                    Utente newUser = new Utente(username, password);
                    System.out.println(newUser);
                    
                    serverRef.registerUser(newUser);
                    System.out.println("USER REGISTERED NOW");
                    return "1";
                }

                /* LOGIN */
            case "2":
                // returns 0 if user is not present, 1 if present
                String[] loginCredentials = splitCredentials(msgRcvd);

                String usernameLogin = loginCredentials[0];
                String passwordLogin = loginCredentials[1];

                // login
                // returns 1 if login was successful, 0 if not
                return serverRef.login(usernameLogin, passwordLogin);

            /* LOGOUT */
            case "3":
                String[] logoutCredentials = splitCredentials(msgRcvd);

                String logoutUsername = logoutCredentials[0];
                return serverRef.logout(logoutUsername);

            /* SEARCH HOTEL */
            case "4":
                // split the received message and return the response
                String[] requestedHotel = splitCredentials(msgRcvd);
                String hotelName = requestedHotel[0];
                String city = requestedHotel[1];

                return searchHotel(hotelName, city);

            /* SEARCH ALL HOTELS IN CITY */
            case "5":
                System.out.println(msgRcvd);
                String[] requestedCity = splitCredentials(msgRcvd);
                String searchCity = requestedCity[0];
                
                return createHotelsString(searchCity);

            /* INSERT REVIEW */
            case "6":
                String[] review = splitCredentials(msgRcvd);
                System.out.println("*****************************************");
                System.out.println(review);
                System.out.println("*****************************************");
                //(int posizione, int pulizia, int servizio, int qualita, String username, int idHotel, int createDate, long ts)
                // get username, hotel name and city
                String reviewerUsername = review[0];
                String reviewedHotelName = review[1];
                String reviewedHotelCity = review[2];
                
                // get all the scores
                int globalScore = Integer.parseInt(review[3]);
                int position = Integer.parseInt(review[4]);
                int cleaning = Integer.parseInt(review[5]);
                int services = Integer.parseInt(review[6]);
                int quality = Integer.parseInt(review[7]);
                int hotelId = getHotelId(reviewedHotelCity, reviewedHotelName);
                
                System.out.println("HOTEL ID: " + hotelId);
                if (hotelId != -1) {
                    // create new review with the received data
                    Recensione newReview = new Recensione(globalScore, position, cleaning, services, quality, reviewerUsername,
                            hotelId, 1, 0);
                    System.out.println(newReview.toString());

                    // update user level
                    serverRef.updateUser(reviewerUsername);

                    // save new data to json
                    addSaveReview(newReview, hotelId);
                    saveUtenteToJson();
                    
                    return "1";
                }
                
                return "-1";

            /* SHOW BADGES */
            case "7":
                // split credentials and get username
                System.out.println("MSG: " + msgRcvd);
                String[] userCredentials = splitCredentials(msgRcvd);
                username = userCredentials[0];

                System.out.println("USERNAME: " + username);
                return serverRef.showBadges(username);

            /* EXIT */
            case "8":
                System.out.println("Ok esco");
                // close selector and socket channel
                return "8";
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

    public void registerUser(Utente newUser) {
        addUserAndSaveToJson(newUser);
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
        String registerPwd = currentUser.password;

        if (registerPwd.equals(password)) {
            loggedInUsersPut(username);
            serverRef.printLoggedIn();
            return "1";
        }

        return "0";
    }

    // returns 1 if logout was successful, 0 if not
    public String logout(String username) {
        loggedInUsers.remove(username);
        System.out.println("removed entry: " + username);
        return "1";
    }

    /* SHOW BADGES */
    public String showBadges(String username) {
        Utente utente = registeredUsers.get(username);

        if (utente != null) {
            String userLevel = utente.getUserLevel();
            System.out.println("User Level for " + username + ": " + userLevel);
            return userLevel;
        }

        return "-1";
    }

    /* SEARCH FUNCTIONS */
    public int getHotelId(String city, String hotelName) {
        // Check if the city exists in the ConcurrentHashMap
        if (hotels.containsKey(city)) {
            // Get the list of hotels for the specified city
            List<Hotel> cityHotels = hotels.get(city);

            // Iterate through the hotels in the city
            for (Hotel hotel : cityHotels) {
                // Check if the hotel name matches
                if (hotel.name.equals(hotelName)) {
                    // Return the hotel ID if found
                    return hotel.id;
                }
            }
        }

        // Return -1 if the hotel is not found
        return -1;
    }
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
        for (Hotel hotel : matchingHotels) {
            System.out.println(hotel);
        }

        return matchingHotels;
    }

    ///////////////////////////
    // UTILITY FUNCTIONS
    ///////////////////////////

    public void printRegistered() {
        System.out.println("Registered Users:");

        for (Map.Entry<String, Utente> entry : registeredUsers.entrySet()) {
            String username = entry.getKey();
            Utente user = entry.getValue();

            System.out.println("Username: " + username);
            System.out.println("Written Reviews Count: " + user.getUserReviewCount());
            // Add any other user information you want to print
            System.out.println("----------------------------");
        }

    }

    public void printLoggedIn() {
        System.out.println("Logged-In Users:");

        for (String username : loggedInUsers) {
            System.out.println("Username: " + username);
            // Add any other logged-in user information you want to print
            System.out.println("----------------------------");
        }
    }

    ///////////////////////////
    // JSON FUNCTIONS
    ///////////////////////////

    /* FUNCTIONS TO LOAD DATA FROM FILE TO CLASS DATA STRUCTURE */

    private static JsonObject readJsonFromFile(String filePath) {
        try {
            String jsonData = new String(Files.readAllBytes(Paths.get(filePath)));
    
            if (jsonData != null && !jsonData.isEmpty()) {
                return JsonParser.parseString(jsonData).getAsJsonObject();
            } else {
                // Handle the case where jsonData is null or empty
                System.out.println("The JSON data is null or empty.");
                // or log an error, throw an exception, or handle it as appropriate for your application
                return null;
            }
        } catch (IOException | JsonSyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /* HOTELS FUNCTIONS */
    private static void loadHotelsFromJson() {
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

    public static void saveHotelsToJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonArray hotelsArray = new JsonArray();

        // Iterate through all cities
        for (Map.Entry<String, List<Hotel>> entry : hotels.entrySet()) {
            String city = entry.getKey();
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
                ratingsJson.addProperty("cleaning", hotel.ratings.getCleaning());
                ratingsJson.addProperty("position", hotel.ratings.getPosition());
                ratingsJson.addProperty("services", hotel.ratings.getServices());
                ratingsJson.addProperty("quality", hotel.ratings.getQuality());

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

    private static void loadUsersFromJson() {
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
    private static void saveUtenteToJson() {
        try {
            JsonObject jsonObject = new JsonObject();
            JsonArray utentiArray = new JsonArray();

            registeredUsers.forEach((key, value) -> utentiArray.add(new Gson().toJsonTree(value)));

            jsonObject.add("utenti", utentiArray);

            try (FileWriter fileWriter = new FileWriter(USERS_JSON_PATH)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(jsonObject, fileWriter);
            }

            System.out.println("Utente data saved successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // function to add a new user to the registered users hashmap and save to json
    public static String addUserAndSaveToJson(Utente newUtente) {
        try {
            // get username and use it as key of insertion
            String username = newUtente.username;

            registeredUsers.put(username, newUtente);
            saveUtenteToJson();
            System.out.println("Utente appended successfully.");

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

            System.out.println("User fields updated successfully");
            return 1;
        }
        return -1;
    }
    /* REVIEWS FUNCTIONS */

    private static void loadReviewsFromJson() {
        try {
            String jsonData = new String(Files.readAllBytes(Paths.get(REVIEWS_JSON_PATH)));
            JsonObject reviewsObject = JsonParser.parseString(jsonData).getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : reviewsObject.entrySet()) {
                String city = entry.getKey();
                JsonArray reviewsArray = entry.getValue().getAsJsonArray();
                List<Recensione> reviewsList = new ArrayList<>();

                for (JsonElement reviewElement : reviewsArray) {
                    JsonObject reviewObject = reviewElement.getAsJsonObject();

                    // Extract review details
                    int totale = reviewObject.get("totale").getAsInt();
                    int posizione = reviewObject.get("posizione").getAsInt();
                    int pulizia = reviewObject.get("pulizia").getAsInt();
                    int servizio = reviewObject.get("servizio").getAsInt();
                    int qualita = reviewObject.get("qualita").getAsInt();
                    int idHotel = reviewObject.get("idHotel").getAsInt();
                    long ts = reviewObject.get("timestamp").getAsLong();
                    String username = reviewObject.get("username").getAsString();

                    // Create a new Recensione
                    Recensione recensione = new Recensione(totale, posizione, pulizia, servizio, qualita, username, idHotel, 0, ts);

                    // Add the review to the list
                    reviewsList.add(recensione);
                }

                // Add the reviews list to the reviews map
                reviews.put(city, reviewsList);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // function to save to json the review
    private static void saveReviewsToJson() {
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
    public static void addReviewToHotel(int hotelId, Recensione recensione) {
        String hotelIdStr = String.valueOf(hotelId);

        // Check if the hotel ID is already in the map
        if (reviews.containsKey(hotelIdStr)) {
            // If yes, add the review to the existing list
            reviews.get(hotelIdStr).add(recensione);
        } else {
            // If no, create a new list and add the review
            List<Recensione> reviewList = new ArrayList<>();
            reviewList.add(recensione);
            reviews.put(String.valueOf(hotelIdStr), reviewList);
        }
    }

    // function to add a review
    public static void addSaveReview(Recensione recensione, int hotelId) {
        addReviewToHotel(hotelId, recensione);
        saveReviewsToJson();
    }

}
