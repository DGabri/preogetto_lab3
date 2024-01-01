import java.util.Date;


public class Recensione {
    public int posizione;
    public int pulizia;
    public int servizio;
    public int qualita;
    public String username;
    public int idHotel;
    public long timestamp;

    
    public Recensione(int posizione, int pulizia, int servizio, int qualita, String username, int idHotel, int createDate, long ts) {
        this.posizione = posizione;
        this.pulizia = pulizia;
        this.servizio = servizio;
        this.qualita = qualita;
        this.username = username;
        this.idHotel = idHotel;
        
        if (createDate == 1) {
            this.timestamp = System.currentTimeMillis();
        }
        else {
            this.timestamp = ts;
        }
    }
    
    /* GETTERS TO BE USED WHEN LOADING JSON */
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

    /* FUNCTION TO PRINT OBJECT */
    @Override
    public String toString() {
        return "Recensione -> " +
                "posizione=" + posizione +
                ", pulizia=" + pulizia +
                ", servizio=" + servizio +
                ", qualita=" + qualita +
                ", username='" + username + '\'' +
                ", idHotel=" + idHotel +
                ", ts=" + timestamp;

    }
    
}
