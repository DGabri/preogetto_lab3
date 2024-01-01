import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

public class Hotel {
    public int id;
    public String name;
    public String description;
    public String city;
    public String phone;
    public List<String> services;
    public double rate;
    public Recensione ratings;

    public Hotel(int id, String name, String description, String city, String phone, List<String> services, double rate,
            int posizione, int pulizia, int servizio, int prezzo) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.city = city;
        this.phone = phone;
        this.services = services;
        this.rate = rate;
        Ratings ratings = new Ratings(0, 0, 0, 0);

    }
            
    public static List<Recensione> loadReviewsFromJson(String filePath) {
        try (FileReader reader = new FileReader(filePath)) {
            // Definisci il tipo di oggetto che desideri deserializzare
            Type listType = new TypeToken<List<Recensione>>() {
            }.getType();

            // Usa Gson per leggere il file JSON e deserializzarlo in una lista di Recensione
            return new Gson().fromJson(reader, listType);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public static void appendReviewToJson(int hotelId, Recensione nuovaRecensione) {
        String filePath = "./assets/reviews.json";
        
        try {
            // Leggi il file JSON esistente
            JsonArray jsonArray = new JsonArray();

            // Aggiungi le recensioni esistenti al JsonArray
            if (new java.io.File(filePath).exists()) {
                jsonArray = new Gson().fromJson(new FileReader(filePath), JsonArray.class);
            }

            // Costruisci un nuovo oggetto JSON per la nuova recensione
            JsonObject nuovaRecensioneJson = new JsonObject();
            nuovaRecensioneJson.addProperty("id", hotelId);
            nuovaRecensioneJson.addProperty("name", "Hotel " + hotelId);
            nuovaRecensioneJson.addProperty("position", nuovaRecensione.posizione);
            nuovaRecensioneJson.addProperty("cleaning", nuovaRecensione.pulizia);
            nuovaRecensioneJson.addProperty("services", nuovaRecensione.servizio);
            nuovaRecensioneJson.addProperty("quality", nuovaRecensione.qualita);

            // Aggiungi il nuovo oggetto JSON al JsonArray
            jsonArray.add(nuovaRecensioneJson);

            try (FileWriter fileWriter = new FileWriter(filePath)) {
                new Gson().toJson(jsonArray, fileWriter);
            } catch (IOException e) {
                e.printStackTrace();
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String toString() {
        return "Hotel{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", city='" + city + '\'' +
                ", phone='" + phone + '\'' +
                ", services=" + services +
                ", rate=" + rate +
                '}';
    }

}
