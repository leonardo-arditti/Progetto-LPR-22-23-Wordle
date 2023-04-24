
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.*;

/**
 * @author Leonardo Arditti 23/4/2023
 */
public class WordleServer {

    // Percorso del file di configurazione del server
    public static final String CONFIG = "src/server.properties";

    // Porta del server e nome del file contenente il vocabolario del gioco
    public static int PORT;
    public static String VOCABULARYFILE;
    public static int TIMEOUT;
    
    private static String secretWord;
    private static ConcurrentHashMap<String, User> users;
            
    /**
     * Metodo che legge il file di configurazione del server
     *
     * @throws FileNotFoundException se il file non esiste
     * @throws IOException se si verifica un errore durante la lettura
     */
    public static void readConfig() throws FileNotFoundException, IOException {
        try (InputStream input = new FileInputStream(CONFIG)) {
            Properties prop = new Properties();
            prop.load(input);
            PORT = Integer.parseInt(prop.getProperty("PORT"));
            VOCABULARYFILE = prop.getProperty("VOCABULARYFILE");
            TIMEOUT = Integer.parseInt(prop.getProperty("TIMEOUT"));
        } catch (IOException ex) {
            System.err.println("Errore durante la lettura del file di configurazione.");
            ex.printStackTrace();
        }
    }

    public static void readUsers() {
        users = new ConcurrentHashMap<String, User>();
        
    }
    
    public static String addUser(String username, String password) {
        if (password.equals(""))
            return "EMPTY";
        else if (users.containsKey(username))
            return "DUPLICATE";
        
        User user = new User(username, password);
        users.put(username, user);
        return "SUCCESS";        
    }
    
    public static void main(String args[]) {
        try {
            // Lettura del file di configurazione del server
            readConfig();
        } catch (IOException ex) {
            System.err.println("Errore nella lettura del file di configurazione.");
            ex.printStackTrace();
        }

        try (ServerSocket welcomeSocket = new ServerSocket(PORT)) {
            welcomeSocket.setSoTimeout(TIMEOUT);
            System.out.println("Server attivo, ascolto sulla porta " + PORT);
            ExecutorService pool = Executors.newCachedThreadPool();
            while (true) {
                pool.execute(new ClientHandler(welcomeSocket.accept()));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
