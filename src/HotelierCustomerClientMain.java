import java.nio.charset.StandardCharsets;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.io.FileInputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.math.BigInteger;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.*;

public class HotelierCustomerClientMain {
    // config variables and asset path
    private static final String CLIENT_CONFIG = "./assets/client.properties";
    private static String SERVER_ADDRESS;
    private static String MULTICAST_ADDR;
    private static int PORT;

    // selector variables
    private MulticastSocket multicastSocket;
    private SocketChannel socketChannel;
    private InetAddress multicastGroup;
    private Selector selector;
    Thread notificationsThread;

    // login state client side
    private boolean loggedIn = false;
    private String username = "";

    public HotelierCustomerClientMain() {
        try {

            // load config
            Properties prop = loadConfig(CLIENT_CONFIG);
            PORT = Integer.parseInt(prop.getProperty("port"));
            SERVER_ADDRESS = prop.getProperty("serverAddress");
            MULTICAST_ADDR = prop.getProperty("multicastAddress");

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

            // multicast socket init
            multicastSocket = new MulticastSocket(PORT + 1);
            multicastSocket.setReuseAddress(true);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        HotelierCustomerClientMain client = new HotelierCustomerClientMain();

        try (Scanner scanner = new Scanner(System.in)) {
            String input;

            while (true) {
                // Display menu
                client.printOptions();

                // Get user input
                input = scanner.nextLine().trim();

                switch (input) {
                    /* REGISTRAZIONE */
                    case "1":
                        System.out.print("Inserisci username: ");
                        String usernameRegister = scanner.nextLine().trim();
                        System.out.print("Inserisci password: ");
                        String passwordRegister = scanner.nextLine().trim();

                        // error if password is empty
                        if (passwordRegister.length() == 0) {
                            System.out.println("Inserirsci una password non vuota");
                            break;
                        }

                        // get response code
                        client.register(usernameRegister, passwordRegister);
                        break;

                    /* LOGIN */
                    case "2":
                        // execute only if user is not logged in
                        if (client.loggedIn == true) {
                            break;
                        }

                        // get username and password and send them to server
                        System.out.print("Inserisci username di login: ");
                        String usernameLogin = scanner.nextLine().trim();
                        System.out.print("Inserisci password di login: ");
                        String passwordLogin = scanner.nextLine().trim();

                        // login
                        client.login(usernameLogin, passwordLogin);
                        break;

                    /* LOGOUT */
                    case "3":
                        // esegui logout se l'username e' presente
                        if (client.username.length() != 0) {
                            client.logout(client.username);
                        } else {
                            System.out.println("Hai gia' effettuato il logout");
                        }
                        break;

                    /* CERCA HOTEL */
                    case "4":
                        System.out.print("Inserisci nome hotel: ");
                        String nomeHotel = scanner.nextLine().trim();
                        System.out.print("Inserisci citta' hotel: ");
                        String citta = scanner.nextLine().trim();

                        client.searchHotel(nomeHotel, citta);
                        break;

                    /* CERCA TUTTI GLI HOTELS IN UNA DETERMINATA CITTA' */
                    case "5":
                        System.out.print("Inserisci citta' per cercare hotel: ");
                        String cittaTuttiHotel = scanner.nextLine().trim();

                        client.searchAllHotels(cittaTuttiHotel);

                        break;

                    /* INSERISCI RECENSIONE */
                    case "6":
                        // if client is not logged in forbid operation
                        if (client.loggedIn == false) {
                            break;
                        }

                        // init empty review
                        int[] reviewPoints = { 0, 0, 0, 0 };

                        System.out.print("Inserisci il nome dell'hotel da recensire: ");
                        String reviewedHotelName = scanner.nextLine().trim();
                        System.out.print("Inserisci la citta' dell'hotel da recensire: ");
                        String reviewedHotelCity = scanner.nextLine().trim();

                        // reviews values
                        System.out.println("Inserisci i punteggi per la recensione, valori ammessi 0-5 inclusi");

                        int positionScore = getReviewPoints("Inserisci un punteggio per la posizione: ", scanner);
                        int cleaningScore = getReviewPoints("Inserisci un punteggio per la pulizia: ", scanner);
                        int serviceScore = getReviewPoints("Inserisci un punteggio per il servizio: ", scanner);
                        int qualityScore = getReviewPoints("Inserisci un punteggio per la qualita': ", scanner);

                        // populate scores array
                        reviewPoints[0] = positionScore;
                        reviewPoints[1] = cleaningScore;
                        reviewPoints[2] = serviceScore;
                        reviewPoints[3] = qualityScore;

                        // calculate global score as the mean of the single scores
                        int globalScore = (reviewPoints[0] + reviewPoints[1] + reviewPoints[2] + reviewPoints[3]) / 4;

                        // send review to server
                        client.insertReview(reviewedHotelName, reviewedHotelCity, globalScore, reviewPoints);
                        break;

                    /* MOSTRA BADGE UTENTE */
                    case "7":
                        // if client is not logged in forbid operation
                        if (client.loggedIn == false) {
                            break;
                        }

                        client.showMyBadges();
                        break;

                    /* ESCI */
                    case "8":
                        // close selector and socket channel
                        writeRead(client.socketChannel, "8_exit");
                        client.selector.close();
                        client.socketChannel.close();
                        // close notifications multicast group if it was started
                        if (client.loggedIn == true) {
                            client.stopThreadAndMulticast();
                            //client.closeNotificationsGroup();
                        }
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

    // function to load configuration file
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

    // function to hash client's password
    public static String hashPassword(String password) {
        try {
            // hash the passwor using sha 256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // convert password to bytes and hash the value
            byte[] msgDigest = digest.digest(password.getBytes());

            // convert hash to integer
            BigInteger intHash = new BigInteger(msgDigest);

            // convert integer to base 16 hex
            return intHash.toString(16);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
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
            System.out.println("6 Inserisci Recensione");
            System.out.println("7 Mostra Livello Utente");
            System.out.println("8 Termina");
            System.out.println("-----------------------");
            System.out.print("Inserisci un valore compreso tra 1 ed 8: ");
        }

    }

    public static void printResponse(String response) {
        System.out.println("*********************************");
        System.out.println("***         RESPONSE          ***");
        System.out.println("*********************************");
        System.out.println("");
        System.out.println(response);
        System.out.println("");
        System.out.println("*********************************");
    }

    // function to check if input is numeric
    public static boolean isNumericInput(String inputValueString) {
        try {
            int test = Integer.parseInt(inputValueString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static int getReviewPoints(String inputMsg, Scanner inp) {
        while (true) {
            System.out.print(inputMsg);
            String input = inp.nextLine();

            // check if input is numeric
            if (isNumericInput(input)) {
                int inputValueInt = Integer.parseInt(input);

                // check if range of [0,5] is respected
                if (!checkScoreRange(inputValueInt)) {
                    System.out.println("Inserire un valore compreso tra 0 e 5");
                } else {
                    return inputValueInt;

                }

            } else {
                System.out.println("Inserire un numero");
            }
        }
    }

    // helper function to check if score is valide
    public static boolean checkScoreRange(int value) {
        return ((value >= 0) && (value <= 5));
    }

    // function to register a new user
    public String register(String username, String password) {

        if ((username.length() != 0) && (password.length() != 0)) {
            // prepare string to send
            String hashedPwd = hashPassword(password);
            String msg = "1_" + username + "_" + hashedPwd;

            // send string and receive response
            String res = writeRead(socketChannel, msg);
            if (res.equals("-1")) {
                printResponse("Utente gia' registrato");
            } else if (res.equals("1")) {
                printResponse("Utente registrato correttamente");
            }

            return res;
        } else {
            System.out.println("Credenziali invalide, riprovare, lunghezza minima > 0");
        }

        return "";
    }

    // function to login
    public void login(String username, String password) {
        // send server(username, password)
        if (!this.loggedIn) {

            if ((username.length() != 0) && (password.length() != 0)) {
                String hashedPwd = hashPassword(password);

                String msg = "2_" + username + "_" + hashedPwd;

                String retCode = writeRead(socketChannel, msg);

                if (retCode.equals("1")) {
                    // save that login was successful
                    this.loggedIn = true;
                    this.username = username;

                    printResponse("Login effettuato");
                    // start thread for multicast and listen to notifications
                    subscribeToMulticastGroup();
                    startNotificationsThread();

                } else if (retCode.equals("-1")) {
                    printResponse("Login ERROR, password errata");
                } else if (retCode.equals("-2")){
                    printResponse("Login ERROR, utente gia' loggato");
                } else {
                    printResponse("Login ERROR, utente non registrato");
                }
            } else {
                System.out.println(" **** INSERT VALID USERNAME AND PASSWORD WITH LENGTH > 0 ****");
            }
        }
    }

    // logout
    public void logout(String username) {

        // prepare string to send
        String msg = "3_" + this.username;

        String retCode = writeRead(socketChannel, msg);

        if (retCode.equals("1")) {
            this.loggedIn = false;
            this.username = "";
            printResponse("Logout effettuato");
            // close multicast group
            //client.closeNotificationsGroup();
            stopThreadAndMulticast();
            System.out.println("GROUP CLOSED");
        } else {
            printResponse("Logout error");
        }
    }

    // function to search 1 hotel
    public String searchHotel(String nomeHotel, String citta) {

        // prepare string to send
        String msg = "4_" + nomeHotel + "_" + citta;
        if ((nomeHotel.length() != 0) || (citta.length() != 0)) {
            String responseHotel = writeRead(socketChannel, msg);
            printResponse(responseHotel);
            return responseHotel;
        }

        return "4_empty";

    }

    // function to ask server for all the hotels in a given city
    public void searchAllHotels(String citta) {

        // prepare string to send
        String msg = "5_" + citta;

        if (citta.length() != 0) {
            String responseHotels = writeRead(socketChannel, msg);
            printResponse("HOTELS per: " + citta + " -> " + "\n" + responseHotels);
        } else {
            System.out.println("Inserire una citta'");
        }
    }

    // function to extract input data and send it to the server
    public void insertReview(String nomeHotel, String nomeCitta, int globalScore, int[] singleScores) {
        if (this.loggedIn) {
            // prepare string to send

            String msg = "6" + "_" + this.username + "_" + nomeHotel + "_" + nomeCitta + "_"
                    + String.valueOf(globalScore) + "_"
                    + String.valueOf(singleScores[0]) + "_" + String.valueOf(singleScores[1]) + "_"
                    + String.valueOf(singleScores[2]) + "_" + String.valueOf(singleScores[3]);

            // send data to server
            String retCode = writeRead(socketChannel, msg);

            if (retCode.equals("1")) {
                printResponse("Recensione inserita correttamente");
            } else if  (retCode.equals("-2")) {
                printResponse("Hotel non presente");
            } else {
                printResponse("Errore nell'inserimento recensione");
            }
        }
    }

    // function to show user badge
    public void showMyBadges() {
        // prepare string to send
        String msg = "7_" + this.username;

        String badgeName = writeRead(socketChannel, msg);
        printResponse("Il tuo badge attuale e': " + badgeName);
    }

    private static String writeRead(SocketChannel socketChannel, String msg) {
        try {
            // convert msg to bytes
            byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);

            // create buffer with lenghth 1 integeer + msg length
            ByteBuffer writeBuffer = ByteBuffer.allocate(Integer.BYTES + msgBytes.length);

            // put the length of the message so server knows how much needs to read
            writeBuffer.putInt(msgBytes.length);
            writeBuffer.put(msgBytes);
            writeBuffer.flip();

            // write effective message until everything is written
            while (writeBuffer.hasRemaining()) {
                socketChannel.write(writeBuffer);
            }

            // get server response length
            ByteBuffer lengthBuffer = ByteBuffer.allocate(Integer.BYTES);
            while (socketChannel.read(lengthBuffer) <= 0) {
            }

            lengthBuffer.flip();
            int responseMsgLength = lengthBuffer.getInt();

            // read response msg
            ByteBuffer responseMsgBuffer = ByteBuffer.allocate(responseMsgLength);

            while (socketChannel.read(responseMsgBuffer) <= 0) {
                // wait for data
            }
            responseMsgBuffer.flip();

            // convert message bytes to string
            byte[] responseStringBytes = new byte[responseMsgBuffer.remaining()];
            responseMsgBuffer.get(responseStringBytes);

            return new String(responseStringBytes, StandardCharsets.UTF_8);

        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    /* UDP NOTIFICATION RECEIVER */
    private void subscribeToMulticastGroup() {
        try {
            multicastGroup = InetAddress.getByName(MULTICAST_ADDR);
            multicastSocket.joinGroup(multicastGroup);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // function to start the notification handler thread
    public void startNotificationsThread() {
        // start udp thread

        this.notificationsThread = new Thread(() -> this.startNotificationReceiver());
        notificationsThread.start();
    }

    // function called by thread to print received message
    public void startNotificationReceiver() {
        try {

            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                this.multicastSocket.receive(packet);

                String received = new String(packet.getData(), 0, packet.getLength());

                // print the received message
                System.out.println("");
                printResponse(received);
                printOptions();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // function to close the multicast group
    public void closeNotificationsGroup() {
        this.multicastSocket.close();
    }

    public void stopThreadAndMulticast() {
        // Interrupt the notificationsThread to stop the loop
        if (notificationsThread != null) {
            notificationsThread.interrupt();
        }

        // Close the socket if it is still open
        if (multicastSocket != null && !multicastSocket.isClosed()) {
            multicastSocket.close();
        }
    }

}
