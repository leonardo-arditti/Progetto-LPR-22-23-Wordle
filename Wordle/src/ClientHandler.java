
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

/** 
 * @author Leonardo Arditti 24/4/2023
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private boolean is_logged = false;
    private boolean has_won = false;
    private User user;
    
    public ClientHandler(Socket socket) {
        this.socket = socket;
    }
    
    @Override
    public void run() {
        try (Scanner in = new Scanner(socket.getInputStream());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true); ) {
            
            while (in.hasNextLine()) {
                String cmd = in.next(); // comando nella sua forma completa, e.g: register(username,password)
                System.out.println("RICEVUTO: "+cmd);
                String[] cmd_components = cmd.split(",");
                String op = cmd_components[0];
                
                switch (op) {
                    case "REGISTER":
                        String username = cmd_components[1];
                        String password = cmd_components[2];
                        System.out.println(username + " " + password);
                        out.println("SUCCESS");
                        break;
                        
                }
            }
            
        } catch (Exception e) {
            System.err.println("Errore nella comunicazione con il client.");
            e.printStackTrace();
        }
    }
}
