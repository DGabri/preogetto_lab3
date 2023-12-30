package demo.src;
import java.util.List;

public class Hotel {
    public int id;
    public String name;
    public String description;
    public String city;
    public String phone;
    public List<String> services;
    public int rate;
    public Recensione ratings;

    public Hotel(int id, String name, String description, String city, String phone, List<String> services, int rate, int posizione, int pulizia, int servizio, int prezzo) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.city = city;
    this.phone = phone;
    this.services = services;
    this.rate = rate;
    this.ratings = new Recensione(posizione, pulizia, servizio, prezzo);
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
