import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import utils.Utils;


public class jsonTest {
    private Map<String, Utente> utenteMap;
    private String JSON_FILE_PATH = "./assets/users.json";
    
    public jsonTest() {

        utenteMap = new ConcurrentHashMap<>();
        loadUtenteData(); // Load Utente data from JSON
        printUtente();
    }

    // Load Utente data from JSON
    private void loadUtenteData() {
        // Assuming you have a method to read JSON data into a JsonObject
        JsonObject jsonData = Utils.readJsonFromFile(JSON_FILE_PATH);

        if (jsonData != null && jsonData.has("utenti")) {
            JsonArray utentiArray = jsonData.getAsJsonArray("utenti");

            for (JsonElement utenteElement : utentiArray) {
                Utente utente = new Gson().fromJson(utenteElement, Utente.class);
                utenteMap.put(utente.name, utente);
            }
        }
    }

    private void appendToUtenteJson(Utente newUtente) {
        try {
            // Read existing JSON data
            String jsonData = new String(Files.readAllBytes(Paths.get(JSON_FILE_PATH)));
            JsonObject jsonObject = JsonParser.parseString(jsonData).getAsJsonObject();

            // Get the "utenti" array or create it if not exists
            JsonArray utentiArray = jsonObject.getAsJsonArray("utenti");
            if (utentiArray == null) {
                utentiArray = new JsonArray();
                jsonObject.add("utenti", utentiArray);
            }

            // Convert newUtente to JsonObject
            Gson gson = new GsonBuilder().create();
            JsonObject utenteObject = gson.toJsonTree(newUtente).getAsJsonObject();

            // Append the new Utente to the array
            utentiArray.add(utenteObject);

            // Write the updated JSON data back to the file
            try (FileWriter fileWriter = new FileWriter(JSON_FILE_PATH)) {
                gson.toJson(jsonObject, fileWriter);
            }

            System.out.println("Utente appended successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    

    private void printUtente() {
        System.out.println("Loaded Utentes:");
        for (Map.Entry<String, Utente> entry : utenteMap.entrySet()) {
            System.out.println(entry.getValue());
        }
    }
}
