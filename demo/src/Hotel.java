
import java.util.List;

public class Hotel {
    public int id;
    public String name;
    public String description;
    public String city;
    public String phone;
    public List<String> services;
    public double rate;
    public List<Recensione> review;

    public Hotel(int id, String name, String description, String city, String phone, List<String> services, double rate,
            int posizione, int pulizia, int servizio, int prezzo) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.city = city;
        this.phone = phone;
        this.services = services;
        this.rate = rate;
        this.ratings = new Recensione(posizione, pulizia, servizio, prezzo);
        this.review = Recensione.loadReviewsFromJson("./assets/reviews.json");

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
                jsonArray = GsonUtils.readJsonArrayFromFile(filePath);
            }

            // Costruisci un nuovo oggetto JSON per la nuova recensione
            JsonObject nuovaRecensioneJson = new JsonObject();
            nuovaRecensioneJson.addProperty("id", hotelId);
            nuovaRecensioneJson.addProperty("name", "Hotel " + hotelId);
            nuovaRecensioneJson.addProperty("posizione", nuovaRecensione.posizione);
            nuovaRecensioneJson.addProperty("pulizia", nuovaRecensione.pulizia);
            nuovaRecensioneJson.addProperty("servizio", nuovaRecensione.servizio);
            nuovaRecensioneJson.addProperty("prezzo", nuovaRecensione.prezzo);

            // Aggiungi il nuovo oggetto JSON al JsonArray
            jsonArray.add(nuovaRecensioneJson);

            // Scrivi il JsonArray aggiornato nel file JSON
            GsonUtils.writeJsonArrayToFile(jsonArray, filePath);
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
                ", ratings=" + ratings +
                '}';
    }

}
