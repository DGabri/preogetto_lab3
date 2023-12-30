
import java.util.List;

public class Hotel {
    private int id;
    private String name;
    private String description;
    private String city;
    private String phone;
    private List<String> services;
    private int rate;
    private Recensione ratings;

    public Hotel(int id, String name, String description, String city, String phone, List<String> services, int rate,
            int recensioneSintetica, int posizione, int pulizia, int servizio, int prezzo) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.city = city;
    this.phone = phone;
    this.services = services;
    this.rate = rate;
    this.ratings = new Recensione(recensioneSintetica, posizione, pulizia, servizio, prezzo);
}

}
