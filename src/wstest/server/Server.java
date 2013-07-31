package wstest.server;

import com.wstest.websocket.handler.WebSocketHandler;
import com.wstest.websocket.listener.WebSocketListener;

public class Server implements Runnable {
    
    // Handlers bound to ports
    private final WebSocketHandler feeder;
    private final WebSocketHandler listener;

    /**
     * Overall server
     * @param feeder the port for the feeder connection to establish
     * @param listener the port for the listener connections to establish
     */
    public Server(int feederPort, int listenerPort) {
        WebSocketListener listener = new WebSocketListener() {
            @Override
            public void onOpen() { 
                System.out.println("WS opened");
            }

            @Override
            public void onClose() { }

            @Override
            public void onMessage(String message) {
                System.out.println(message);
            }
        };
        
        this.feeder = new WebSocketHandler("Feeder", feederPort, listener);
        this.listener = new WebSocketHandler("Listener", listenerPort, listener);
    }
     
    @Override
    public void run() {
        Thread feederThread = new Thread(feeder);
        Thread listenerThread = new Thread(listener);
        
        try {
            feederThread.start();
            listenerThread.start();
        } catch (Exception e) {
            // Moderately fucked
            
            try {
                feederThread.join();
                listenerThread.join();
            } catch (Exception ex) {
                // Royally fucked
            }
        }
    }
    
    public static void main(String... args) {
        Server server = new Server(8080, 88);
        server.run();
    }
}
