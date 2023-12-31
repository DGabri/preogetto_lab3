import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

public class App {

    private static final String HOTELS_JSON_PATH = "./assets/Hotels.json";
    private static String USERS_JSON_PATH = "./assets/users.json";
    private static String REVIEWS_JSON_PATH = "./assets/revies.json";

    private static ConcurrentHashMap<String, Utente> utenteMap;
    public static ConcurrentHashMap<Integer, List<Recensione>> reviews;

    public static void main(String[] args) {
        // Esempio di utilizzo
        List<Hotel> hotels = readHotelsFromFile();
        loadReviewsFromJson(REVIEWS_JSON_PATH);
        //List<Hotel> rankedHotels = calculateLocalRank(hotels);

        // Stampa i risultati

        // Create an example hotel
        /* 
         
        Hotel hotel = new Hotel(1, "Hotel Aosta 1", "Un ridente hotel a Aosta, in Via della gioia, 25",
        "Aosta", "347-4453634", List.of("TV in camera", "Palestra", "Cancellazione gratuita"),
        0, 0, 0, 0, 0);
        
        // Append the hotel to the file
        appendHotelToFile(hotel);
        
        // Read hotels from the file
        List<Hotel> hotels = readHotelsFromFile();
        
        // Print the hotels
        
        utenteMap = new ConcurrentHashMap<>();
        loadUtenteData();
        utenteMap.forEach((key, value) -> System.out.println(value));
        */
        for (Hotel h : hotels) {
            System.out.println(h);
        }

    }

    private static JsonObject readJsonFromFile(String filePath) {
        try {
            String jsonData = new String(Files.readAllBytes(Paths.get(filePath)));
            return JsonParser.parseString(jsonData).getAsJsonObject();
        } catch (IOException | JsonSyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static List<Hotel> readHotelsFromFile() {
        try {
            String jsonData = new String(Files.readAllBytes(Paths.get(HOTELS_JSON_PATH)));
            Gson gson = new Gson();
            JsonArray jsonArray = gson.fromJson(jsonData, JsonArray.class);

            List<Hotel> hotels = new ArrayList<>();
            for (JsonElement element : jsonArray) {
                hotels.add(gson.fromJson(element, Hotel.class));
            }

            return hotels;
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private static void appendHotelToFile(Hotel hotel) {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonArray jsonArray;

            // Read existing JSON data
            if (Files.exists(Paths.get(HOTELS_JSON_PATH))) {
                String jsonData = new String(Files.readAllBytes(Paths.get(HOTELS_JSON_PATH)));
                jsonArray = gson.fromJson(jsonData, JsonArray.class);
            } else {
                jsonArray = new JsonArray();
            }

            // Convert the Hotel to JsonObject and append to array
            JsonObject hotelObject = gson.toJsonTree(hotel).getAsJsonObject();
            jsonArray.add(hotelObject);

            // Write the updated JSON data back to the file
            try (FileWriter fileWriter = new FileWriter(HOTELS_JSON_PATH)) {
                gson.toJson(jsonArray, fileWriter);
            }

            System.out.println("Hotel appended successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadUtenteData() {
        JsonObject jsonData = readJsonFromFile(USERS_JSON_PATH);

        if (jsonData != null && jsonData.has("utenti")) {
            JsonArray utentiArray = jsonData.getAsJsonArray("utenti");

            for (int i = 0; i < utentiArray.size(); i++) {
                try {
                    Utente utente = new Gson().fromJson(utentiArray.get(i), Utente.class);
                    utenteMap.put(utente.name, utente);
                } catch (JsonSyntaxException e) {
                    System.err.println("Error deserializing Utente: " + e.getMessage());
                }
            }
        }
    }

    private static void appendToUtenteJson(Utente newUtente) {
        try {
            utenteMap.put(newUtente.name, newUtente);
            saveUtenteData();
            System.out.println("Utente appended successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void saveUtenteData() {
        try {
            JsonObject jsonObject = new JsonObject();
            JsonArray utentiArray = new JsonArray();

            utenteMap.forEach((key, value) -> utentiArray.add(new Gson().toJsonTree(value)));

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

    /*
    public static List<Hotel> calculateLocalRank(List<Hotel> hotels) {
        // Step 1: Calcolo della Qualità delle Recensioni
        for (Hotel hotel : hotels) {
            double media = (hotel.ratings.posizione + hotel.ratings.pulizia + hotel.ratings.servizio + hotel.ratings.prezzo) / 4.0;
            hotel.rate = media;
        }

        // Step 2: Calcolo della Quantità delle Recensioni
        hotels.sort(Comparator.comparingInt(hotel -> hotel.ratings.getReviewCount()));
        int maxReviewCount = hotels.get(hotels.size() - 1).ratings.getReviewCount();

        // Step 3: Calcolo dell'Attualità delle Recensioni (ipotetico)
        hotels.forEach(hotel -> {
            // Supponiamo che un hotel con recensioni più recenti abbia un punteggio più alto per l'attualità
            int recentnessScore = 10; // Puoi calcolare questo punteggio in base alla data delle recensioni
            hotel.rate += recentnessScore;
        });

        // Step 5: Calcolo del Rank Locale
        hotels.sort(Comparator.comparingDouble(Hotel::getTotalScore).reversed());
        assignLocalRanks(hotels);

        return hotels;
    }
    
    
    private static void normalizeScores(List<Hotel> hotels, int maxReviewCount) {
        // Normalizzazione della quantità delle recensioni
        hotels.forEach(hotel -> {
            double normalizedReviewCount = (double) hotel.ratings.getReviewCount() / maxReviewCount;
            hotel.rate *= normalizedReviewCount;
        });
    }
    
    private static void assignLocalRanks(List<Hotel> hotels) {
        for (int i = 0; i < hotels.size(); i++) {
            hotels.get(i).localRank = i + 1;
        }
    }
    */
    public static void loadReviewsFromJson(String filePath) {
        try {
            String jsonData = new String(Files.readAllBytes(Paths.get(filePath)));
            Gson gson = new Gson();
            JsonArray reviewsArray = JsonParser.parseString(jsonData).getAsJsonArray();

            for (JsonElement element : reviewsArray) {
                JsonObject jsonReview = element.getAsJsonObject();

                // Extract review details
                int hotelId = jsonReview.get("hotelId").getAsInt();
                int posizione = jsonReview.get("posizione").getAsInt();
                int pulizia = jsonReview.get("pulizia").getAsInt();
                int servizio = jsonReview.get("servizio").getAsInt();
                int prezzo = jsonReview.get("prezzo").getAsInt();

                // Create a new Recensione
                Recensione recensione = new Recensione(posizione, pulizia, servizio, prezzo);

                // Add the review to the relative hotel
                addReviewToHotel(hotelId, recensione);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void addReviewToHotel(int hotelId, Recensione recensione) {
        // Check if the hotel ID is already in the map
        if (reviews.containsKey(hotelId)) {
            // If yes, add the review to the existing list
            reviews.get(hotelId).add(recensione);
        } else {
            // If no, create a new list and add the review
            List<Recensione> reviewList = new ArrayList<>();
            reviewList.add(recensione);
            reviews.put(hotelId, reviewList);
        }
    }

    private static Hotel searchHotel(String nomeHotel, String citta) {

        for (Hotel hotel : hotels) {
            // Check if the hotel matches the specified name and city
            if (hotel.name.equalsIgnoreCase(nomeHotel) && hotel.city.equalsIgnoreCase(citta)) {
                return hotel;
            }
        }
    }
    
    private static void searchAllHotels(String citta) {
        List<Hotel> matchingHotels = new ArrayList<>();

        for (Hotel hotel : hotels) {
            // Check if the hotel is in the specified city
            if (hotel.city.equalsIgnoreCase(citta)) {
                matchingHotels.add(hotel);
            }
        }

        for (Hotel h : matchingHotels) {
            System.out.println(h);
        }
    }
}


