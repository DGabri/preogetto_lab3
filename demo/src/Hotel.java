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

    public Hotel(int id, String name, String description, String city, String phone, List<String> services, double rate) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.city = city;
        this.phone = phone;
        this.services = services;
        this.rate = rate;
        Ratings ratings = new Ratings(0, 0, 0, 0);

    }

    public String toString() {
        return "Hotel -> " +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", city='" + city + '\'' +
                ", phone='" + phone + '\'' +
                ", services=" + services +
                ", rate=" + rate;
    }

}
