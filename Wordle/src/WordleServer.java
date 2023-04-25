
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.stream.JsonReader; // in particolare si userà la GSON Streaming API per sfruttare il caricamento parziale di parti di oggetti di grandi dimensioni
import java.io.FileReader;
import java.util.ArrayList;

/**
 * @author Leonardo Arditti 23/4/2023
 */
public class WordleServer {

    private static int connectedUsers = 0; // per scopi di stampe
    
    // Percorso del file di configurazione del server
    public static final String CONFIG = "src/server.properties";

    // Porta del server e nome del file contenente il vocabolario del gioco
    public static int PORT;
    public static String VOCABULARYFILE;
    public static int TIMEOUT;
    public static String USER_DB;
    
    private static String secretWord;
    private static ConcurrentHashMap<String, User> users = new ConcurrentHashMap<String, User>();
    private static ConcurrentHashMap<String, Boolean> usersThatHaveAlreadyPlayed = new ConcurrentHashMap<String, Boolean>();        
    
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
            USER_DB = prop.getProperty("USER_DB");
        } catch (IOException ex) {
            System.err.println("Errore durante la lettura del file di configurazione.");
            ex.printStackTrace();
        }
    }

    public static void loadUsersFromJSON() {
        try (JsonReader reader = new JsonReader(new FileReader(USER_DB))) {
            reader.beginArray();
            while (reader.hasNext()) {
                String username = "", password = "";
                int total_games_played = 0, total_games_won = 0, current_winstreak = 0, longest_winstreak = 0;
                boolean has_played = false;
                ArrayList<Integer> guess_distribution = new ArrayList<>();
                
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    System.out.println(key);
                    if ("username".equals(key)) {
                        username = reader.nextString();
                        System.out.println(username);
                    } else if ("password".equals(key)) {
                        password = reader.nextString();
                        System.out.println(password);
                    } else if ("total_played_games".equals(key)) {
                        total_games_played = reader.nextInt();
                        System.out.println(total_games_played);
                    } else if ("total_games_won".equals(key)) {
                        total_games_won = reader.nextInt();
                    } else if ("current_winstreak".equals(key)) {
                        current_winstreak = reader.nextInt();
                    } else if ("longest_winstreak".equals(key)) {
                        longest_winstreak = reader.nextInt();
                    } else if ("has_played".equals(key)) {
                        has_played = reader.nextBoolean();
                    } else if ("guess_distribution".equals(key)) {
                        reader.beginArray();
                        while (reader.hasNext()) {
                            int guess_attempt = reader.nextInt();
                            guess_distribution.add(guess_attempt);
                        }
                        reader.endArray();
                    }
                }
                reader.endObject();
                User user = new User(username, password, total_games_played, total_games_won, current_winstreak, longest_winstreak, has_played, guess_distribution);
                users.put(username, user);                
            }
            reader.endArray();
            
            // Stampa mappa per debug
            users.forEach((k,v)-> System.out.println("key: "+k+", value: "+v));
        } catch (FileNotFoundException ex) {
            System.err.println("Errore, file '"+USER_DB+"' non trovato.");
            ex.printStackTrace();
        } catch (IOException ex) {
            System.err.println("Errore durante la lettura del file " + USER_DB);
           ex.printStackTrace();
        }
    }
    
    private static void saveUsersToJSON() {
        
    }
    
    /**
    * Aggiunge un nuovo utente alla mappa <Utente, Username>
    *
    * @param username il nome utente del nuovo utente
    * @param password la password del nuovo utente
    * @return una stringa che indica l'esito dell'operazione 
    */ 
    public static String addUser(String username, String password) {
        if (password.equals(""))
            return "EMPTY";
        else if (users.containsKey(username))
            return "DUPLICATE";
        
        User user = new User(username, password);
        users.put(username, user);
        return "SUCCESS";        
    }
    
    public static User getUser(String username) {
        return users.get(username);
    }
    
    public static void updateUser(User user) {
        users.replace(user.getUsername(), user);
    }
    
    public static void main(String args[]) {
        try {
            // Lettura del file di configurazione del server
            readConfig();
            loadUsersFromJSON();
        } catch (IOException ex) {
            System.err.println("Errore nella lettura del file di configurazione.");
            ex.printStackTrace();
        }

        try (ServerSocket welcomeSocket = new ServerSocket(PORT)) {
            // welcomeSocket.setSoTimeout(TIMEOUT);
            System.out.println("Server attivo, ascolto sulla porta " + PORT);
            ExecutorService pool = Executors.newCachedThreadPool();
            while (true) {
                pool.execute(new ClientHandler(welcomeSocket.accept(), connectedUsers++));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
