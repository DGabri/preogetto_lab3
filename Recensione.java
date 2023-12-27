import java.util.*;

public class Recensione {
    private String[] nomiRecensioni = { "Sintetica", "Posizione", "Pulizia", "Servizio", "Prezzo" };
    private int[] recensione = { 0, 0, 0, 0, 0 };
    public boolean recensioneValida = true;
    
    public Recensione(int recensioneSintetica, int posizione, int pulizia, int servizio, int prezzo) {
        recensione[0] = recensioneSintetica;
        recensione[1] = posizione;
        recensione[2] = pulizia;
        recensione[3] = servizio;
        recensione[4] = prezzo;
        checkReview();
    }

    private void checkReview() {

        for (int i = 0; i < recensione.length; i++) {
            int review = recensione[i];
            if (review < 0 || review > 5) {
                // flag to set invalid review
                recensioneValida = false;
                System.out.println("Il punteggio per la recensione: " + nomiRecensioni[i]
                        + " e' invalido, recensione non pubblicabile");
            }
        }
    }    
}
