
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;
import java.util.Map;
import java.util.HashSet;

import com.google.gson.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.stream.Collectors;

public class HotelierServer {
    // private static final String CONFIG = "./assets/server.properties";
    private static final int PORT = 63490;

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
        // Initialize data structures
        registeredUsers = new ConcurrentHashMap<>();
        loggedInUsers = new HashSet<>();
        reviews = new ConcurrentHashMap<>();
        hotels = new ConcurrentHashMap<>();

        // Load data from JSON files
        loadUsersFromJson();
        loadHotelsFromJson();
        loadReviewsFromJson();

        for (String hotelId : reviews.keySet()) {

            List<Recensione> reviewsList = reviews.get(hotelId);
            for (Recensione review : reviewsList) {
                System.out.println(review);
            }
        }
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
    
    
    
    public void readMsg1(SelectionKey selKey, Selector selector) {
        try {
            // open socket channel and read to buff
            SocketChannel socketChannel = (SocketChannel) selKey.channel();
            ByteBuffer buff = ByteBuffer.allocate(1024);
            int bytesRead = socketChannel.read(buff);

            if (bytesRead == -1) {
                socketChannel.close();
                System.out.println("Client closed connection");
                return;
            }

            // read msg from client and echo it
            buff.flip();
            byte[] data = new byte[buff.remaining()];
            buff.get(data);
            String messageReceived = new String(data, "UTF-8");
            System.out.println("RECEIVED: " + messageReceived);

            // handle the received msg and send "back response
            String msgToSend = serverRef.handleReceivedMessage(messageReceived);
            // Echo the message back to the client
            socketChannel.write(ByteBuffer.wrap(msgToSend.getBytes("UTF-8")));
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
                String[] credentials = Utils.splitCredentials(rcvdCredentials);

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
                    serverRef.registerUser(newUser);
                    System.out.println("USER REGISTERED NOW");
                    return "1";
                }

                /* LOGIN */
            case "2":
                // returns 0 if user is not present, 1 if present
                String[] loginCredentials = Utils.splitCredentials(msgRcvd);

                String usernameLogin = loginCredentials[0];
                String passwordLogin = loginCredentials[1];

                // login
                // returns 1 if login was successful, 0 if not
                return serverRef.login(usernameLogin, passwordLogin);

            /* LOGOUT */
            case "3":
                String[] logoutCredentials = Utils.splitCredentials(msgRcvd);

                String logoutUsername = logoutCredentials[0];
                return serverRef.logout(logoutUsername);

            /* SEARCH HOTEL */
            case "4":
                // split the received message and return the response
                String[] requestedHotel = Utils.splitCredentials(msgRcvd);
                String hotelName = requestedHotel[0];
                String city = requestedHotel[1];

                return searchHotel(hotelName, city);

            /* SEARCH ALL HOTELS IN CITY */
            case "5":
                System.out.println(msgRcvd);
                String[] requestedCity = Utils.splitCredentials(msgRcvd);
                String searchCity = requestedCity[0];
                
                return createHotelsString(searchCity);

            /* INSERT REVIEW */
            case "6":
                return "0";

            /* SHOW BADGES */
            case "7":
                // split credentials and get username
                String[] userCredentials = Utils.splitCredentials(msgRcvd);
                username = userCredentials[0];

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
            return JsonParser.parseString(jsonData).getAsJsonObject();
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
        try (FileWriter writer = new FileWriter("./assets/test.json")) {
            gson.toJson(hotelsArray, writer);
            System.out.println("All hotels data saved ");
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
                    System.err.println("Error deserializing Utente: " + e.getMessage());
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
                    int posizione = reviewObject.get("posizione").getAsInt();
                    int pulizia = reviewObject.get("pulizia").getAsInt();
                    int servizio = reviewObject.get("servizio").getAsInt();
                    int prezzo = reviewObject.get("prezzo").getAsInt();
                    int idHotel = reviewObject.get("idHotel").getAsInt();
                    String username = reviewObject.get("username").getAsString();

                    // Create a new Recensione
                    Recensione recensione = new Recensione(posizione, pulizia, servizio, prezzo, username, idHotel);

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

    private static void loadReviewsFromJson1() {
        try {
            String jsonData = new String(Files.readAllBytes(Paths.get(REVIEWS_JSON_PATH)));
            JsonObject reviewsObject = JsonParser.parseString(jsonData).getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : reviewsObject.entrySet()) {
                String hotelIdStr = entry.getKey();
                int hotelId = Integer.parseInt(hotelIdStr);

                JsonArray reviewsArray = entry.getValue().getAsJsonArray();
                for (JsonElement reviewElement : reviewsArray) {
                    JsonObject reviewObject = reviewElement.getAsJsonObject();

                    // Extract review details
                    int posizione = reviewObject.get("posizione").getAsInt();
                    int pulizia = reviewObject.get("pulizia").getAsInt();
                    int servizio = reviewObject.get("servizio").getAsInt();
                    int prezzo = reviewObject.get("prezzo").getAsInt();
                    int idHotel = reviewObject.get("idHotel").getAsInt();
                    String username = reviewObject.get("username").getAsString();

                    // Create a new Recensione
                    Recensione recensione = new Recensione(posizione, pulizia, servizio, prezzo, username, idHotel);

                    // Add the review to the relative hotel
                    addReviewToHotel(hotelId, recensione);
                }
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
