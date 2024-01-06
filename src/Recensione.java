import java.util.Date;


public class Recensione {
    public double totale;
    public double posizione;
    public double pulizia;
    public double servizio;
    public double qualita;
    public String username;
    public int idHotel;
    public long timestamp;

    
    public Recensione(double totale, double posizione, double pulizia, double servizio, double qualita, String username, int idHotel, int createDate, long ts) {
        this.totale = totale;
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
    public double getCleaning() {
        return this.pulizia;
    }

    public double getPosition() {
        return this.posizione;
    }

    public double getServices() {
        return this.servizio;
    }

    public double getQuality() {
        return this.qualita;
    }

    /* FUNCTION TO PRINT OBJECT */
    @Override
    public String toString() {
        return "Recensione -> " +
                "totale=" + totale +
                ", posizione=" + posizione +
                ", pulizia=" + pulizia +
                ", servizio=" + servizio +
                ", qualita=" + qualita +
                ", username='" + username + '\'' +
                ", idHotel=" + idHotel +
                ", ts=" + timestamp;

    }
    
}
