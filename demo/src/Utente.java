
public class Utente {
    public int writtenReviewsCount;
    public String userLevel;
    public String username;
    public String password;

    public Utente(String username, String password) {
        this.username = username;
        this.password = password;
        this.writtenReviewsCount = 0;
        this.setUserLevel();
    }

    // function to increase by 1 the number of reviews done to keep statistics
    public void increaseReviewCount() {
        this.writtenReviewsCount += 1;
    }

    /* GETTERS FOR USER LEVEL AND REVIEWS WRITTEN */
    public String getUserLevel() {
        String level = this.userLevel;
        return level;
    }
    
    public int getUserReviewCount() {
        int reviewsCount = this.writtenReviewsCount;
        return reviewsCount;
    }

    /* SET USER LEVEL */
    public void setLevel(String level) {
        this.userLevel = level;
    }

    /* FUNCTION TO CALCULATE CURRENT LEVEL */
    public void setUserLevel() {

        int count = this.writtenReviewsCount;

        if ((count >= 0) && (count < 3)) {
            setLevel("Recensore");
        } else if ((count >= 3) && (count < 6)) {
            setLevel("Recensore_Esperto");
        } else if ((count >= 6) && (count < 8)) {
            setLevel("Contributore");
        } else if ((count >= 8) && (count < 10)) {
            setLevel("Contributore_Esperto");
        } else if (count >= 10) {
            setLevel("Contributore_Super");
        }

    }
    
    /* FUNCTION TO PRINT OBJECT */
    public String toString() {
        return "Utente{" +
                "writtenReviewsCount=" + writtenReviewsCount +
                ", userLevel='" + userLevel + '\'' +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                '}';
    }
}
