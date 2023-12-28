import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.net.InetSocketAddress;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import utils.Utils;

public class HotelierCustomerClient {
    // config variables and asset path
    private static final String CONFIG = "./assets/client.properties";
    private static final int BUFFER_SIZE = 1024;
    private static String SERVER_ADDRESS;
    private static int PORT;

    // selector variables
    private SocketChannel socketChannel;
    private Selector selector;
    private boolean loggedIn = false;

    public HotelierCustomerClient() {
        try {
            // load config
            Properties prop = Utils.loadConfig(CONFIG);
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

            System.out.println("Connecting to socket");

            while (!socketChannel.finishConnect()) {
            }

            System.out.println("Connected");

        } catch (IOException e) {
            e.printStackTrace(); // Handle the exception based on your requirements
        }
    }

    public static void main(String[] args) {
        HotelierCustomerClient client = new HotelierCustomerClient();

        try (Scanner scanner = new Scanner(System.in)) {
            String input;

            while (true) {
                // Display menu
                printOptions();

                // Get user input
                input = scanner.nextLine();

                switch (input) {
                    /* REGISTER */
                    case "1":
                        System.out.print("Inserisci username: ");
                        String usernameRegister = scanner.nextLine();
                        System.out.print("Inserisci password: ");
                        String passwordRegister = scanner.nextLine();

                        client.register(usernameRegister, passwordRegister);
                        break;

                    /* LOGIN */
                    case "2":
                        // get username and password and split it to login
                        System.out.print("Inserisci username login: ");
                        String usernameLogin = scanner.nextLine();
                        System.out.print("Inserisci password login: ");
                        String passwordLogin = scanner.nextLine();

                        // login
                        client.login(usernameLogin, passwordLogin);
                        break;

                    /* LOGOUT */
                    case "3":
                        client.logout();
                        break;

                    /* SEARCH HOTEL */
                    case "4":
                        System.out.print("Inserisci nome hotel: ");
                        String nomeHotel = scanner.nextLine();
                        System.out.print("Inserisci citta' hotel: ");
                        String citta = scanner.nextLine();

                        System.out.print("Hotel: " + nomeHotel + " citta: " + citta);
                        client.searchHotel(nomeHotel, citta);
                        break;

                    /* SEARCH ALL HOTELS IN CITY */
                    case "5":
                        System.out.print("Inserisci citta' per cercare hotel: ");
                        String cittaTuttiHotel = scanner.nextLine();

                        System.out.print("Citta': " + cittaTuttiHotel);
                        client.searchAllHotels(cittaTuttiHotel);
                        break;

                    /* INSERT REVIEW */
                    case "6":
                        int[] reviewPoints = { 2, 2, 2, 5 };
                        client.insertReview("nome", "Venezia", 3, reviewPoints);
                        break;

                    /* SHOW BADGES */
                    case "7":
                        client.showMyBadges();
                        break;

                    /* EXIT */
                    case "8":
                        System.out.println("Ok esco");
                        // close selector and socket channel
                        writeRead(client.socketChannel, "8");
                        client.selector.close();
                        client.socketChannel.close();
                        System.exit(0);

                    default:
                        System.out.println("Valore non corretto, inserisci un valore tra quelli elencati");
                        break;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // function to print options available
    private static void printOptions() {
        System.out.println("-----------------------");
        System.out.println("Scegli azione:");
        System.out.println("1 Sign Up");
        System.out.println("2 Login");
        System.out.println("3 Logout");
        System.out.println("4 Cerca Hotel");
        System.out.println("5 Cerca alberghi in una citta'");
        System.out.println("6 Inserisci Recensiones");
        System.out.println("7 Mostra Livello Utente");
        System.out.println("8 Termina");
        System.out.println("-----------------------");
        System.out.print("Inserisci un valore compreso tra 1 ed 8: ");
    }

    // function to get username and password from cmd
    public String getUsernamePwd() {
        String username = "";
        String password = "";

        // get username and password from cmd
        try (Scanner scanner = new Scanner(System.in)) {
            do {
                System.out.print("Inserisci username: ");
                username = scanner.nextLine();

                System.out.print("Inserisci password: ");
                password = scanner.nextLine();

                if (username.length() == 0 || password.length() == 0) {
                    System.out.println("Username e password devono essere non vuoti. Riprova.");
                }
            } while (username.length() == 0 || password.length() == 0);
;
            return new String(username + "_" + password);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    public void register(String username, String password) {

        if ((username.length() != 0) && (password.length() != 0)) {
            // server send credentials
            String msg = "1_" + username + "_" + password;
            System.out.println("MSG: " + msg);

            String res = writeRead(socketChannel, msg);
            System.out.println("RECEIVED: " + res);
        } else {
            System.out.println("Credenziali invalide, riprova, lunghezza minima > 0");
        }
    }

    public int login(String username, String password) {
        // send server(username, password)
        if (!this.loggedIn) {

            String msg = "2_" + username + "_" + password;
            System.out.println("MSG: " + msg);

            String retCode = writeRead(socketChannel, msg);
            System.out.println("Login return code: " + retCode);

            if (retCode.equals("1")) {
                this.loggedIn = true;
            } else {
                System.out.println("Login again, error");
            }
        }

        return 1;
    }

    // logout
    public void logout() {

        String msg = "3_logout";
        System.out.println("MSG: " + msg);

        String retCode = writeRead(socketChannel, msg);
        System.out.println("Login return code: " + retCode);

        if (retCode.equals("1")) {
            this.loggedIn = false;
        } else {
            System.out.println("Logout error");
        }
    }

    public void searchHotel(String nomeHotel, String citta) {

        String msg = "4_" + nomeHotel + "_" + citta;
        System.out.println("MSG: " + msg);

        String retCode = writeRead(socketChannel, msg);
        System.out.println("Return code: " + retCode);
    }

    public void searchAllHotels(String citta) {

        String msg = "5_" + citta;
        System.out.println("MSG: " + msg);

        String retCode = writeRead(socketChannel, msg);
        System.out.println("Return code: " + retCode);
    }

    public void insertReview(String nomeHotel, String nomeCitta, int globalScore, int[] singleScores) {
        if (this.loggedIn) {
            String msg = "6" + nomeHotel + "_" + nomeCitta + "_" + String.valueOf(globalScore) + "_"
                    + String.valueOf(singleScores[0]) + "_" + String.valueOf(singleScores[1]) + "_"
                    + String.valueOf(singleScores[2]) + "_" + String.valueOf(singleScores[3]) + "_"
                    + String.valueOf(singleScores[4]);

            System.out.println("MSG: " + msg);

            String retCode = writeRead(socketChannel, msg);
            System.out.println("Return code: " + retCode);
        } else {
            System.out.println("Login necessario per inserire recensione");
        }
    }

    public String showMyBadges() {
        if (this.loggedIn) {

            String msg = "7_showBadges";

            String retCode = writeRead(socketChannel, msg);
            System.out.println("RCVD: " + retCode);
            return msg;
        } else {
            System.out.println("Login necessario per vedere badges");
            return "";
        }
    }

    public void exitClient() {
        if (this.loggedIn) {

            Utils.sendData(socketChannel, '8', "exit");
        } else {
            System.out.println("Sto uscendo");
        }
    }

    private static String writeRead(SocketChannel socketChannel, String msg) {
        try {
            ByteBuffer writeBuffer = ByteBuffer.wrap(msg.getBytes());
            socketChannel.write(writeBuffer);

            ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);

            // wait server
            while (socketChannel.read(readBuffer) <= 0) {
                // wait data
            }

            // flip buffer to read response
            readBuffer.flip();

            byte[] responseData = new byte[readBuffer.remaining()];
            readBuffer.get(responseData);

            String response = new String(responseData, "UTF-8");
            System.out.println("Echo: " + response);

            return response;
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

}
