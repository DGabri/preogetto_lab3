import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import javax.sound.midi.Receiver;

import java.net.InetSocketAddress;
import java.util.Comparator;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import com.google.gson.*;
import java.util.*;
import java.io.*;

public class Prova {
    private static final String[] capoluoghi = {"L'Aquila", "Potenza", "Catanzaro", "Napoli", "Bologna", "Trieste", "Roma",
            "Genova", "Milano", "Ancona", "Campobasso", "Torino", "Bari", "Cagliari", "Palermo", "Firenze", "Trento",
            "Perugia", "Aosta", "Venezia" };
    // json files path
    private static final String HOTELS_JSON_PATH = "./assets/Hotels.json";
    private static String USERS_JSON_PATH = "./assets/users.json";
    private static String REVIEWS_JSON_PATH = "./assets/reviews.json";
    // config variables
    // Users tracker, hotels and reviews
    private static Map<String, Utente> registeredUsers;
    private static Set<String> loggedInUsers;
    private static Map<String, List<Recensione>> reviews;
    private static Map<String, List<Hotel>> hotels;

    // object reference to call methods
    private static Prova serverRef;

    public Prova() {

        // Initialize data structures
        registeredUsers = new HashMap<>();
        loggedInUsers = new HashSet<>();
        reviews = new HashMap<>();
        hotels = new HashMap<>();

        // Load data from JSON files
        loadUsersFromJson();
        loadHotelsFromJson();
        loadReviewsFromJson();
    }


    public static void main(String[] args) {
        serverRef = new Prova();

        // put start time so after RANKING_REFRESH_RATE i can check if a new ranking is
        // calculated
        serverRef.printReviews(reviews, hotels);
        serverRef.printHotels();
        serverRef.printRegistered();

        String newTopHotels = serverRef.recalculateRanking();

        serverRef.getUserReviewHotel(2, "gabri");
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



    ///////////////////////////
    // UTILITY FUNCTIONS
    ///////////////////////////

    public int isValidCity(String city) {
            for (String capoluogo : capoluoghi) {
                if (capoluogo.equalsIgnoreCase(city)) {
                    return 1;
                }
            }
            return 0;
    }

    public void printReviews(Map<String, List<Recensione>> reviews, Map<String, List<Hotel>> hotels) {

        for (Map.Entry<String, List<Recensione>> entry : reviews.entrySet()) {
            String city = entry.getKey();
            List<Recensione> reviewList = entry.getValue();

            System.out.println("City: " + city);
            System.out.println("Reviews:");

            for (Recensione review : reviewList) {
                int hotelId = review.idHotel;
                List<Hotel> hotelList = hotels.get(city);

                if (hotelList != null) {
                    // Find the hotel with the corresponding ID using a for loop
                    Hotel foundHotel = null;
                    for (Hotel hotel : hotelList) {
                        if (hotel.id == hotelId) {
                            foundHotel = hotel;
                            break;
                        }
                    }

                    if (foundHotel != null) {
                        System.out.println("Hotel: " + foundHotel.name);
                    }
                }

                System.out.println(review);
                System.out.println("---------------------");
            }

            System.out.println("---------------------");
        }
    }

    private void printHotels() {
        System.out.println("Hotels Information:");
        for (Map.Entry<String, List<Hotel>> entry : hotels.entrySet()) {
            String city = entry.getKey();
            List<Hotel> hotelList = entry.getValue();

            System.out.println("City: " + city);
            System.out.println("Hotels:");

            for (Hotel hotel : hotelList) {
                System.out.println(hotel);
            }

            System.out.println("---------------------");
        }
    }

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

    private JsonObject readJsonFromFile(String filePath) {
        try {
            String jsonData = new String(Files.readAllBytes(Paths.get(filePath)));

            if (jsonData != null && !jsonData.isEmpty()) {
                return JsonParser.parseString(jsonData).getAsJsonObject();
            } else {
                System.out.println("The JSON data is null");
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

            System.out.println("Utente data saved successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // function to add a new user to the registered users hashmap and save to json
    public String addUserAndSaveToJson(Utente newUtente) {
        try {
            // get username and use it as key of insertion
            String username = newUtente.username;

            registeredUsers.put(username, newUtente);
            serverRef.saveUtenteToJson();
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
                    int totale = reviewObject.get("totale").getAsInt();
                    int posizione = reviewObject.get("posizione").getAsInt();
                    int pulizia = reviewObject.get("pulizia").getAsInt();
                    int servizio = reviewObject.get("servizio").getAsInt();
                    int qualita = reviewObject.get("qualita").getAsInt();
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

    private void loadReviewsFromJson1() {
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
                    Recensione recensione = new Recensione(totale, posizione, pulizia, servizio, qualita, username,
                            idHotel, 0, ts);

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
        return ((double) reviewsList.size()) / 10;
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

    // function to calculate the delta between last and first review timestamp
    public double getReviewsActuality(List<Recensione> reviewsList) {

        // sort reviewsList by timestamp descending
        reviewsList.sort((r1, r2) -> Long.compare(r2.timestamp, r1.timestamp));

        // delta between last and first (temporal) review
        if (!reviewsList.isEmpty()) {
            // first review timestamp
            long now = System.currentTimeMillis();
            // last review timestamp
            long lastTimestamp = reviewsList.get(0).timestamp;

            // get last digit
            long delta = (now - lastTimestamp) % 100;
            // scale by 10
            return (delta / 10);
        }

        // if no review return 0
        return 0;
    }

    // function to get reviews quality
    public double getReviewsQualityPerHotel(List<Recensione> reviewsList) {
        // calculate mean score
        int listLength = reviewsList.size();
        int totalScore = 0;
        int meanScore = 0;

        for (Recensione review : reviewsList) {
            totalScore += review.totale;
        }
        
        if (listLength != 0) {
            meanScore = (totalScore / listLength);
            return ((double) meanScore) / 10;
        }
        
        return 0;
    }

    // function to sort hotels by score, score is calculated using calculateScore()
    public void sortHotelsByScore(Map<String, List<Recensione>> reviews, Map<String, List<Hotel>> hotels) {
        if ((reviews == null) || (hotels == null)) {
            // Handle the case where reviews is null, e.g., return an empty list or throw an exception
            return;
        }

        for (Map.Entry<String, List<Recensione>> entry : reviews.entrySet()) {
            String city = entry.getKey();
            List<Recensione> reviewList = entry.getValue();

            System.out.println("City: " + city);
            System.out.println("Sorted Hotels by Score:");

            // Create a map to store the calculated score for each hotel
            Map<Integer, Double> scores = new HashMap<>();

            // Calculate the score for each hotel
            for (Recensione review : reviewList) {
                int hotelId = review.idHotel;

                scores.compute(hotelId, (key, value) -> {
                    // calculate score for each hotel
                    System.out.println("  ");
                    double score = serverRef.calculateScore(hotelId, city);
                    System.out.println("SCORE FOR: " + hotelId + " SCORE: " + score);
                    return score;
                });
            }

            // Sort hotels based on the calculated score
            List<Hotel> hotelList = hotels.get(city);
            if (hotelList != null) {
                // sort descending
                hotelList.sort(Comparator.comparingDouble((Hotel hotel) -> scores.getOrDefault(hotel.id, 0.0)).reversed());

                // Print sorted hotels
                for (Hotel hotel : hotelList) {
                    System.out.println("Hotel: " + hotel.name + ", Score: " +
                            scores.getOrDefault(hotel.id, 0.0));
                }
            }

            System.out.println("---------------------");
        }
    }

    // function to update hotel rating based on new reviews
    public void updateHotelGlobalRate() {
        for (String city : hotels.keySet()) {
            List<Hotel> hotelList = hotels.get(city);

            for (Hotel hotel : hotelList) {
                List<Recensione> cityReviews = reviews.get(city);
                List<Recensione> hotelReviewsList = serverRef.getReviewsForHotel(cityReviews, hotel.id);

                // calculate total reate as the mean of all reviews rate for that hotel
                double totalRate = serverRef.getReviewsQualityPerHotel(hotelReviewsList);

                // Update the hotel's rate
                hotel.rate = totalRate;
            }
        }

        // persist update to json
        saveHotelsToJson();
    }

    // function to sort the ranking of the hotels
    public String recalculateRanking() {
        // first update all the hotels score
        serverRef.updateHotelGlobalRate();

        String result = "";
        // calculate ranking (sort hotels)
        for (String city : hotels.keySet()) {
            List<Hotel> hotelList = hotels.get(city);

            // get name of actual top hotel
            String actualTopHotelName = hotelList.isEmpty() ? null : hotelList.get(0).name;

            // sort hotels for each city
            serverRef.sortHotelsByScore(reviews, hotels);

            // get name of new first hotel
            String newTopHotelName = hotelList.isEmpty() ? null : hotelList.get(0).name;

            System.out.println("New Top Hotel: " + newTopHotelName);
            // check if there is a change
            if (!actualTopHotelName.equals(newTopHotelName)) {
                System.out.println("Change in first position for city " + city);
                System.out.println("Previous Top Hotel: " + actualTopHotelName);
                System.out.println("New Top Hotel: " + newTopHotelName);
                result += newTopHotelName + "_" + city + "-";
            }
        }

        // save hotels to json
        serverRef.saveHotelsToJson();
        return result;
    }

    // function called when sorting hotels, it assigns a value to each hotel based
    // on
    // 1. number of reviews
    // 2. reviews actuality
    // 3. reviews quality
    public double calculateScore(int hotelId, String city) {
        List<Recensione> cityReviews = reviews.get(city);

        List<Recensione> hotelReviews = serverRef.getReviewsForHotel(cityReviews, hotelId);
        for (Recensione review : hotelReviews) {
            System.out.println(review.toString());
        }

        // quantita' recensioni
        double reviewsCount = serverRef.getReviewsCountPerHotel(hotelReviews);

        // attualita' recensioni
        double reviewsActuality = serverRef.getReviewsActuality(hotelReviews);

        // qualita' recensioni
        double reviewsQuality = serverRef.getReviewsQualityPerHotel(hotelReviews);

        System.out.println("ID: " + hotelId + " COUNT: " + reviewsCount + " ACTUALITY: " + reviewsActuality
                + " QUALITY: " + reviewsQuality + " TOTAL: " + (reviewsCount + reviewsActuality + reviewsQuality));
        return reviewsCount + reviewsActuality + reviewsQuality;
    }
    

    // function to get the last review a user did to a hotel
    // sorted descending so the first is the last review done
    public Recensione getUserReviewHotel(int hotelId, String username) {
        List<Recensione> userReviews = new ArrayList<>();

        for (List<Recensione> reviewsList : reviews.values()) {
            for (Recensione review : reviewsList) {
                if (review.idHotel == hotelId && review.username.equals(username)) {
                    userReviews.add(review);
                }
            }
        }

        // sort
        Collections.sort(userReviews, (r1, r2) -> Long.compare(r2.timestamp, r1.timestamp));

        return userReviews.get(0);
    }

    public boolean canWriteReview(String username, int hotelId) {
        Recensione lastReview = serverRef.getUserReviewHotel(hotelId, username);

        long lastReviewTimestamp = lastReview.timestamp;
        long now = System.currentTimeMillis();

        long delta = (now - lastReviewTimestamp) / 1000;

        // check if the time between last review and now is greater than 1 minute
        return (delta > 60);
    }
}

