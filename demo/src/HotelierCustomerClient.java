import java.nio.charset.StandardCharsets;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.net.InetSocketAddress;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

public class HotelierCustomerClient {
    // config variables and asset path
    private static final String CLIENT_CONFIG = "./assets/client.properties";
    private static String SERVER_ADDRESS;
    private static int PORT;

    // selector variables
    private SocketChannel socketChannel;
    private Selector selector;
    private boolean loggedIn = false;

    private String username = "";

    public HotelierCustomerClient() {
        try {
            // load config
            Properties prop = loadConfig(CLIENT_CONFIG);
            PORT = Integer.parseInt(prop.getProperty("port"));
            SERVER_ADDRESS = prop.getProperty("serverAddress");

            System.out.println("PORT: " + PORT + " ADDR: " + SERVER_ADDRESS);
            // Open selector and socket channel
            selector = Selector.open();

            // connect
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.connect(new InetSocketAddress(SERVER_ADDRESS, PORT));
            socketChannel.register(selector, SelectionKey.OP_CONNECT);

            // wait for socket to be completely connected
            while (!socketChannel.finishConnect()) {
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        HotelierCustomerClient client = new HotelierCustomerClient();

        try (Scanner scanner = new Scanner(System.in)) {
            String input;

            while (true) {
                // Display menu
                client.printOptions();

                // Get user input
                input = scanner.nextLine();

                switch (input) {
                    /* REGISTRAZIONE */
                    case "1":
                        System.out.print("Inserisci username: ");
                        String usernameRegister = scanner.nextLine();
                        System.out.print("Inserisci password: ");
                        String passwordRegister = scanner.nextLine();

                        // error if password is empty
                        if (passwordRegister.length() == 0) {
                            System.out.println("Inserirsci una password non vuota");
                            break;
                        }

                        // get response code
                        String resCode = client.register(usernameRegister, passwordRegister);
                        if (resCode.equals("-1")) {
                            System.out.println("Utente gia' registrato");
                        }
                        System.out.println("******************************");

                        break;

                    /* LOGIN */
                    case "2":
                        // get username and password and send them to server
                        System.out.print("Inserisci username di login: ");
                        String usernameLogin = scanner.nextLine();
                        System.out.print("Inserisci password di login: ");
                        String passwordLogin = scanner.nextLine();

                        // login
                        client.login(usernameLogin, passwordLogin);
                        System.out.println("******************************");
                        break;

                    /* LOGOUT */
                    case "3":
                        // esegui logout se l'username e' presente
                        if (client.username.length() != 0) {
                            client.logout(client.username);
                        } else {
                            System.out.println("Hai gia' effettuato il logout");
                        }
                        System.out.println("******************************");
                        break;

                    /* CERCA HOTEL */
                    case "4":
                        System.out.print("Inserisci nome hotel: ");
                        String nomeHotel = scanner.nextLine();
                        System.out.print("Inserisci citta' hotel: ");
                        String citta = scanner.nextLine();

                        client.searchHotel(nomeHotel, citta);

                        System.out.println("******************************");
                        break;

                    /* CERCA TUTTI GLI HOTELS IN UNA DETERMINATA CITTA' */
                    case "5":
                        System.out.print("Inserisci citta' per cercare hotel: ");
                        String cittaTuttiHotel = scanner.nextLine();

                        System.out.print("Citta': " + cittaTuttiHotel);
                        client.searchAllHotels(cittaTuttiHotel);
                        System.out.println("******************************");
                        break;

                    /* INSERISCI RECENSIONE */
                    case "6":
                        // init empty review
                        int[] reviewPoints = { 0, 0, 0, 0 };

                        System.out.print("********************************");
                        System.out.print("Inserisci il nome dell'hotel da recensire: ");
                        String reviewedHotelName = scanner.nextLine();
                        System.out.print("Inserisci la citta' dell'hotel da recensire: ");
                        String reviewedHotelCity = scanner.nextLine();
                        
                        // reviews values
                        System.out.println("Inserisci i punteggi per la recensione, valori ammessi 0-5 inclusi");
                        System.out.print("Inserisci un punteggio per la posizione: ");
                        int positionScore = Integer.parseInt(scanner.nextLine());
                        System.out.print("Inserisci un punteggio per la pulizia: ");
                        int cleaningScore = Integer.parseInt(scanner.nextLine());
                        System.out.print("Inserisci un punteggio per il servizio: ");
                        int serviceScore = Integer.parseInt(scanner.nextLine());
                        System.out.print("Inserisci un punteggio per la qualita': ");
                        int qualityScore = Integer.parseInt(scanner.nextLine());

                        // check that points are between [0,5]
                        // if false a score is wrong, break and try again
                        if (!(client.checkScoreRange(positionScore) && client.checkScoreRange(cleaningScore)
                                && client.checkScoreRange(serviceScore) && client.checkScoreRange(qualityScore))) {
                            break;
                        }
                     
                        // populate scores array
                        reviewPoints[0] = positionScore;
                        reviewPoints[1] = cleaningScore;
                        reviewPoints[2] = serviceScore;
                        reviewPoints[3] = qualityScore;

                        // calculate global score as the mean of the single scores
                        int globalScore = (reviewPoints[0] + reviewPoints[1] + reviewPoints[2] + reviewPoints[3]) / 4;
                        
                        // send review to server
                        client.insertReview(reviewedHotelName, reviewedHotelCity, globalScore, reviewPoints);
                        System.out.println("******************************");
                        break;

                    /* MOSTRA BADGE UTENTE */
                    case "7":
                        client.showMyBadges();
                        System.out.println("******************************");
                        break;

                    /* ESCI */
                    case "8":
                        System.out.println("Ok esco");
                        // close selector and socket channel
                        writeRead(client.socketChannel, "8_exit");
                        client.selector.close();
                        client.socketChannel.close();
                        System.exit(0);

                    default:
                        System.out.println("Valore non corretto, inserisci un valore tra quelli elencati");
                        System.out.println("******************************");
                        break;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Properties loadConfig(String fname) {
        try (FileInputStream fileInputStream = new FileInputStream(fname)) {
            Properties properties = new Properties();
            properties.load(fileInputStream);

            return properties;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // function to print options available
    private void printOptions() {
        // display these lines only if not logged in
        if (!this.loggedIn) {
            System.out.println("-----------------------");
            System.out.println("Scegli azione:");
            System.out.println("1 Sign Up");
            System.out.println("2 Login");
            System.out.println("4 Cerca Hotel");
            System.out.println("5 Cerca alberghi in una citta'");
            System.out.println("8 Termina");
            System.out.println("-----------------------");
            System.out.print("Inserisci un valore compreso tra 1 ed 8: ");
        }
        if (this.loggedIn) {
            System.out.println("-----------------------");
            System.out.println("Scegli azione:");
            System.out.println("3 Logout");
            System.out.println("4 Cerca Hotel");
            System.out.println("5 Cerca alberghi in una citta'");
            System.out.println("6 Inserisci Recensiones");
            System.out.println("7 Mostra Livello Utente");
            System.out.println("8 Termina");
            System.out.println("-----------------------");
            System.out.print("Inserisci un valore compreso tra 1 ed 8: ");
        }

    }

    // helper function to check if score is valide
    public boolean checkScoreRange(int value) {
        return ((value >= 0) && (value <= 5));
    }

    // function to register a new user
    public String register(String username, String password) {

        if ((username.length() != 0) && (password.length() != 0)) {
            // prepare string to send
            String msg = "1_" + username + "_" + password;

            // send string and receive response
            String res = writeRead(socketChannel, msg);
            System.out.println("-----> RESPONSE: " + res);
            return res;
        } else {
            System.out.println("Credenziali invalide, riprova, lunghezza minima > 0");
        }

        return "";
    }

    public int login(String username, String password) {
        // send server(username, password)
        if (!this.loggedIn) {

            String msg = "2_" + username + "_" + password;

            String retCode = writeRead(socketChannel, msg);
            

            if (retCode.equals("1")) {
                // save that login was successful
                this.loggedIn = true;
                this.username = username;
                System.out.println("-----> RESPONSE: Login effettuato");
            } else {
                System.out.println("-----> RESPONSE: Login ERROR, prova di nuovo");
                return 0;
            }
        }

        return 1;
    }

    // logout
    public void logout(String username) {

        // prepare string to send
        String msg = "3_" + username;

        String retCode = writeRead(socketChannel, msg);


        if (retCode.equals("1")) {
            this.loggedIn = false;
            this.username = "";
            System.out.println("-----> RESPONSE: Logout effettuato");

        } else {
            System.out.println("-----> RESPONSE: ERROR Logout");
        }
    }

    public String searchHotel(String nomeHotel, String citta) {

        // prepare string to send
        String msg = "4_" + nomeHotel + "_" + citta;

        String responseHotel = writeRead(socketChannel, msg);
        System.out.println("-----> RESPONSE: HOTEL -> " + "\n" + responseHotel);

        return responseHotel;
    }

    public void searchAllHotels(String citta) {

        // prepare string to send
        String msg = "5_" + citta;

        String responseHotels = writeRead(socketChannel, msg);
        System.out.println("-----> RESPONSE: HOTELS per: " + citta +" -> " + "\n" + responseHotels);
    }

    public void insertReview(String nomeHotel, String nomeCitta, int globalScore, int[] singleScores) {
        if (this.loggedIn) {
            // prepare string to send
            System.out.println(singleScores);
            String msg = "6" + "_" + this.username + "_" + nomeHotel + "_" + nomeCitta + "_"
                    + String.valueOf(globalScore) + "_"
                    + String.valueOf(singleScores[0]) + "_" + String.valueOf(singleScores[1]) + "_"
                    + String.valueOf(singleScores[2]) + "_" + String.valueOf(singleScores[3]);

            String retCode = writeRead(socketChannel, msg);
            System.out.println("-----> RESPONSE:" + retCode);
        } 
    }

    public void showMyBadges() {
        if (this.loggedIn) {
            // prepare string to send
            String msg = "7_" + this.username;

            String badgeName = writeRead(socketChannel, msg);
            System.out.println("Il tuo badge attuale e': " + badgeName);
        } 
    }

    private static String writeRead(SocketChannel socketChannel, String msg) {
        try {
            // Convert the message to bytes
            byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);

            // Create a ByteBuffer with length prefix
            ByteBuffer writeBuffer = ByteBuffer.allocate(Integer.BYTES + msgBytes.length);
            writeBuffer.putInt(msgBytes.length);
            writeBuffer.put(msgBytes);
            writeBuffer.flip();

            // Write the message to the server
            while (writeBuffer.hasRemaining()) {
                socketChannel.write(writeBuffer);
            }

            // Read the response length
            ByteBuffer lengthBuffer = ByteBuffer.allocate(Integer.BYTES);
            while (socketChannel.read(lengthBuffer) <= 0) {
                // wait for data
            }
            lengthBuffer.flip();
            int responseLength = lengthBuffer.getInt();

            // Read the actual response
            ByteBuffer readBuffer = ByteBuffer.allocate(responseLength);
            while (socketChannel.read(readBuffer) <= 0) {
                // wait for data
            }
            readBuffer.flip();

            // Convert the received bytes to a String
            byte[] responseData = new byte[readBuffer.remaining()];
            readBuffer.get(responseData);

            return new String(responseData, StandardCharsets.UTF_8);

        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

}
