package demo.src;

import java.util.concurrent.ConcurrentHashMap;
import java.net.InetSocketAddress;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;
import java.util.Map;


public class HotelierServer {
    // private static final String CONFIG = "./assets/server.properties";
    private static final int PORT = 63490;

    // Users tracker
    private ConcurrentHashMap<String, String> registeredUsers;
    private ConcurrentHashMap<String, Integer> loggedInUsers;
    private Map<String, Utente> utenti;


    private static HotelierServer serverRef;

    public HotelierServer() {
        // hashmaps to kkep track of logged and registered users
        registeredUsers = new ConcurrentHashMap<>();
        loggedInUsers = new ConcurrentHashMap<>();
    }

    public static void main(String[] args) {
        serverRef = new HotelierServer();

        try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
                Selector selector = Selector.open()) {

            serverSocketChannel.bind(new InetSocketAddress(PORT));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.printf("[SERVER] Listening on port %d\n", PORT);

            while (true) {
                selector.select();
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();

                    if (key.isAcceptable()) {
                        serverRef.acceptConnection(key, selector);
                    } else if (key.isReadable()) {
                        serverRef.readMsg(key, selector);
                    } 

                    iter.remove();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void acceptConnection(SelectionKey selKey, Selector selector) {
        try {
            // open serverSocket with channel, and accept connection
            ServerSocketChannel serverSocket = (ServerSocketChannel) selKey.channel();
            SocketChannel socketChannel = serverSocket.accept();
            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_READ);
            // Attach a ByteBuffer to store client data
            socketChannel.keyFor(selector).attach(ByteBuffer.allocate(1024));

            System.out.println("Client connected: " + socketChannel.getRemoteAddress());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void readMsg(SelectionKey selKey, Selector selector) {
        try {
            // open socket channel and read to buff
            SocketChannel socketChannel = (SocketChannel) selKey.channel();
            ByteBuffer buff = ByteBuffer.allocate(1024);
            int bytesRead = socketChannel.read(buff);

            if (bytesRead == -1) {
                socketChannel.close();
                System.out.println("Client closed connection");
                return;
            }

            // read msg from client and echo it
            buff.flip();
            byte[] data = new byte[buff.remaining()];
            buff.get(data);
            String messageReceived = new String(data, "UTF-8");
            System.out.println("RECEIVED: " + messageReceived);

            // handle the received msg and send "back response
            String msgToSend = serverRef.handleReceivedMessage(messageReceived);
            // Echo the message back to the client
            socketChannel.write(ByteBuffer.wrap(msgToSend.getBytes("UTF-8")));
            selKey.interestOps(SelectionKey.OP_READ); // Set interest back to read for the next message

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String handleReceivedMessage(String inputMsg) {
        // take substring of the message
        String msgRcvd = inputMsg.substring(2);
        
        // take the first character to do operation switch
        switch (inputMsg.substring(0,1)) {
            /* REGISTER */
            case "1":
                // returns 0 if user is not present, 1 if present
                String rcvdCredentials = msgRcvd;
                String[] credentials = Utils.splitCredentials(rcvdCredentials);
                
                String username = credentials[0];
                String password = credentials[1];

                System.out.println("Username: " + username + " Password: " + password);
                String isRegistered = serverRef.isRegistered(username);
                
                serverRef.printRegistered();
                // user is already registered
                if (isRegistered.equals("1")) {
                    System.out.println("USER ALREADY REGISTERED");
                    return "-1";
                }
                // need to register user
                else if (isRegistered.equals("0")) {
                    serverRef.registerUser(username, password);
                    serverRef.printRegistered();
                    return "1";
                }

            /* LOGIN */
            case "2":
                // returns 0 if user is not present, 1 if present
                String[] loginCredentials = Utils.splitCredentials(msgRcvd);
                
                String usernameLogin = loginCredentials[0];
                String passwordLogin = loginCredentials[1];

                // login
                // returns 1 if login was successful, 0 if not
                return serverRef.login(usernameLogin, passwordLogin);

            /* LOGOUT */
            case "3":
                String[] logoutCredentials = Utils.splitCredentials(msgRcvd);
                
                String logoutUsername = logoutCredentials[0];
                return serverRef.logout(logoutUsername);

            /* SEARCH HOTEL */
            case "4":
                return "0";

            /* SEARCH ALL HOTELS IN CITY */
            case "5":
                return "0";

            /* INSERT REVIEW */
            case "6":
                return "0";

            /* SHOW BADGES */
            case "7":
                
                return "0";

            /* EXIT */
            case "8":
                System.out.println("Ok esco");
                // close selector and socket channel
                return "8";
        }

        return "";
    }
    
    /* REGISTRATION FUNCTIONS */
    public String isRegistered(String username) {
        // returns 0 if user is not present, 1 if present
        return registeredUsersExists(username);
    }
    

    // function to check if username is present in registered users hashmap
    public String registeredUsersExists(String username) {
        if (registeredUsers.containsKey(username)) {
            return "1";
        }

        return "0";
    }
    
    public String registerUser(String username, String password) {
        if (!registeredUsers.containsKey(username)) {
            registeredUsers.put(username, password);
            return "1";
        }

        return "0";
    }

    /* LOGIN FUNCTIONS */
    // check if user is logged in
    public String loggedInUsersExists(String username) {
        if (loggedInUsers.containsKey(username)) {
            return "1";
        }

        return "0";
    }
    
    // insert that user has made a login
    public String loggedInUsersPut(String username) {
        if (!loggedInUsers.containsKey(username)) {
            loggedInUsers.put(username, 1);
            return "1";
        }

        return "0";
    }

    // returns 1 if login was successful, 0 if not
    public String login(String username, String password) {
        String registerPwd = registeredUsers.get(username);

        if (registerPwd.equals(password)) {
            loggedInUsersPut(username);
            serverRef.printLoggedIn();
            return "1";
        }

        return "0";
    }

    // returns 1 if logout was successful, 0 if not
    public String logout(String username) {
        loggedInUsers.remove(username);
        System.out.println("removed entry: " + username);
        return "1";
    }

    /* SHOW BADGES */

    public String showBadges(String username) {
        Utente utente = utenti.get(username);

        if (utente != null) {
            return utente.userLevel;
        } else {
            return "User not found";
        }
    }

    ///////////////////////////
    // UTILITY FUNCTIONS
    ///////////////////////////

    public void printRegistered() {
        for (Map.Entry<String, String> entry : registeredUsers.entrySet()) {
            System.out.println("Key: " + entry.getKey() + ", Value: " + entry.getValue());
        }
        System.out.println("-------------------");
    }
    
    public void printLoggedIn() {
        for (Map.Entry<String, Integer> entry : loggedInUsers.entrySet()) {
            System.out.println("Key: " + entry.getKey() + ", Value: " + entry.getValue());
        }
           System.out.println("-------------------");
    }
    
}
