

public class Recensione {
    public int posizione;
    public int pulizia;
    public int servizio;
    public int qualita;
    public String username;
    public int idHotel;
    
    public Recensione(int posizione, int pulizia, int servizio, int qualita, String username, int idHotel) {
        this.posizione = posizione;
        this.pulizia = pulizia;
        this.servizio = servizio;
        this.qualita = qualita;
        this.username = username;
        this.idHotel = idHotel;
    }
    public int getCleaning() {
        return this.pulizia;
    }

    public int getPosition() {
        return this.posizione;
    }

    public int getServices() {
        return this.servizio;
    }

    public int getQuality() {
        return this.qualita;
    }
    @Override
    public String toString() {
        return "Recensione{" +
                "posizione=" + posizione +
                ", pulizia=" + pulizia +
                ", servizio=" + servizio +
                ", qualita=" + qualita +
                ", username='" + username + '\'' +
                ", idHotel=" + idHotel +
                '}';
    }
    
}
