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
    private static final String CONFIG = "./assets/client.properties";
    private static final int BUFFER_SIZE = 1024;
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
            Properties prop = loadConfig(CONFIG);
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

                        // error if password is empty
                        if (passwordRegister.length() == 0) {
                            System.out.println("Inserirsci una password non vuota");
                            break;
                        }

                        // get response code
                        String resCode = client.register(usernameRegister, passwordRegister);
                        if (resCode.equals("-1")) {
                            System.out.println("User already registered");
                        }
                        System.out.println("******************************");

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
                        System.out.println("******************************");
                        break;

                    /* LOGOUT */
                    case "3":
                        if (client.username.length() != 0) {
                            client.logout(client.username);
                        }
                        else {
                            System.out.println("Hai gia' effettuato il logout");
                        }
                        System.out.println("******************************");
                        break;

                    /* SEARCH HOTEL */
                    case "4":
                        System.out.print("Inserisci nome hotel: ");
                        String nomeHotel = scanner.nextLine();
                        System.out.print("Inserisci citta' hotel: ");
                        String citta = scanner.nextLine();

                        System.out.print("Hotel: " + nomeHotel + " citta: " + citta);
                        resCode = client.searchHotel(nomeHotel, citta);

                        System.out.println("******************************");
                        break;

                    /* SEARCH ALL HOTELS IN CITY */
                    case "5":
                        System.out.print("Inserisci citta' per cercare hotel: ");
                        String cittaTuttiHotel = scanner.nextLine();

                        System.out.print("Citta': " + cittaTuttiHotel);
                        client.searchAllHotels(cittaTuttiHotel);
                        System.out.println("******************************");
                        break;

                    /* INSERT REVIEW */
                    case "6":
                        int[] reviewPoints = { 2, 2, 2, 5 };
                        client.insertReview("nome", "Venezia", 3, reviewPoints);
                        System.out.println("******************************");
                        break;

                    /* SHOW BADGES */
                    case "7":
                        client.showMyBadges();
                        System.out.println("******************************");
                        break;

                    /* EXIT */
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

    public String register(String username, String password) {

        if ((username.length() != 0) && (password.length() != 0)) {
            // prepare string to send
            String msg = "1_" + username + "_" + password;

            // send string and receive response
            String res = writeRead(socketChannel, msg);
            System.out.println("RECEIVED: " + res);
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
            System.out.println("MSG: " + msg);

            String retCode = writeRead(socketChannel, msg);
            System.out.println("Login return code: " + retCode);

            if (retCode.equals("1")) {
                // save that login was successful
                this.loggedIn = true;
                this.username = username;
            } else {
                System.out.println("Login again, error");
            }
        }

        return 1;
    }

    // logout
    public void logout(String username) {

        // prepare string to send
        String msg = "3_" + username;

        String retCode = writeRead(socketChannel, msg);
        System.out.println("Logout return code: " + retCode);

        if (retCode.equals("1")) {
            this.loggedIn = false;
            this.username = "";
            System.out.println("Logged out");
        } else {
            System.out.println("Logout error");
        }
    }

    public String searchHotel(String nomeHotel, String citta) {

        // prepare string to send
        String msg = "4_" + nomeHotel + "_" + citta;
        System.out.println("MSG: " + msg);

        String retCode = writeRead(socketChannel, msg);
        System.out.println("Return code: " + retCode);

        return retCode;
    }

    public void searchAllHotels(String citta) {

        // prepare string to send
        String msg = "5_" + citta;
        System.out.println("MSG: " + msg);

        String retCode = writeRead(socketChannel, msg);
        System.out.println("Return code: " + retCode);
    }

    public void insertReview(String nomeHotel, String nomeCitta, int globalScore, int[] singleScores) {
        if (this.loggedIn) {
            // prepare string to send
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

    public void showMyBadges() {
        if (this.loggedIn) {
            // prepare string to send
            String msg = "7_showBadges";

            String badgeName = writeRead(socketChannel, msg);
            System.out.println("Il tuo badge attuale e': " + badgeName);
        } else {
            System.out.println("Login necessario per vedere badges");
        }
    }

    private static String writeRead1(SocketChannel socketChannel, String msg) {
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

            return response;
        } catch (IOException e) {
            e.printStackTrace();
            return "";
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
