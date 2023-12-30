
public class Recensione {
    private String[] reviewNames = { "Sintetica", "Posizione", "Pulizia", "Servizio", "Prezzo" };
    private int[] review = { 0, 0, 0, 0, 0 };
    public boolean validReview = true;
    
    public Recensione(int recensioneSintetica, int posizione, int pulizia, int servizio, int prezzo) {
        review[0] = recensioneSintetica;
        review[1] = posizione;
        review[2] = pulizia;
        review[3] = servizio;
        review[4] = prezzo;
        checkReview();
    }

    private void checkReview() {

        for (int i = 0; i < review.length; i++) {
            int currentReview = review[i];
            if (currentReview < 0 || currentReview > 5) {
                // flag to set invalid review
                validReview = false;
                System.out.println("Il punteggio per la review: " + reviewNames[i]
                        + " e' invalido, review non pubblicabile");
            }
        }
    }    
}
