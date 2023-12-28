import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

public class HotelierServer {
    // private static final String CONFIG = "./assets/server.properties";
    private static final int PORT = 63490;

    public static void main(String[] args) {
        // HotelierServer server = new HotelierServer();

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
                        acceptConnection(key, selector);
                    } else if (key.isReadable()) {
                        readMsg(key, selector);
                    } 

                    iter.remove();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void acceptConnection(SelectionKey selKey, Selector selector) {
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

    private static void readMsg(SelectionKey selKey, Selector selector) throws IOException {
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
            // Echo the message back to the client
            socketChannel.write(ByteBuffer.wrap(messageReceived.getBytes("UTF-8")));
            selKey.interestOps(SelectionKey.OP_READ); // Set interest back to read for the next message

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
