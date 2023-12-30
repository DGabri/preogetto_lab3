package demo.src;

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
import java.util.concurrent.ConcurrentHashMap;

public class App {

    private static final String HOTELS_FILE_PATH = "./assets/Hotels.json";
    private static String JSON_FILE_PATH = "./assets/users.json";

    private static ConcurrentHashMap<String, Utente> utenteMap;

    public static void main(String[] args) {
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
        for (Hotel h : hotels) {
            System.out.println(h);
        }
        */

        utenteMap = new ConcurrentHashMap<>();
        loadUtenteData();
        utenteMap.forEach((key, value) -> System.out.println(value));
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
            String jsonData = new String(Files.readAllBytes(Paths.get(HOTELS_FILE_PATH)));
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
            if (Files.exists(Paths.get(HOTELS_FILE_PATH))) {
                String jsonData = new String(Files.readAllBytes(Paths.get(HOTELS_FILE_PATH)));
                jsonArray = gson.fromJson(jsonData, JsonArray.class);
            } else {
                jsonArray = new JsonArray();
            }

            // Convert the Hotel to JsonObject and append to array
            JsonObject hotelObject = gson.toJsonTree(hotel).getAsJsonObject();
            jsonArray.add(hotelObject);

            // Write the updated JSON data back to the file
            try (FileWriter fileWriter = new FileWriter(HOTELS_FILE_PATH)) {
                gson.toJson(jsonArray, fileWriter);
            }

            System.out.println("Hotel appended successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadUtenteData() {
        JsonObject jsonData = readJsonFromFile(JSON_FILE_PATH);

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

            try (FileWriter fileWriter = new FileWriter(JSON_FILE_PATH)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(jsonObject, fileWriter);
            }

            System.out.println("Utente data saved successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}


