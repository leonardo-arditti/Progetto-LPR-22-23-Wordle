
import java.net.Socket;

/** 
 * @author Leonardo Arditti 24/4/2023
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    
    public ClientHandler(Socket socket) {
        this.socket = socket;
    }
    
    @Override
    public void run() {
        
    }
}
