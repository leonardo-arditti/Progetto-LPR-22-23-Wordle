
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
import com.google.gson.stream.JsonWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * @author Leonardo Arditti 23/4/2023
 */

/*
* NB: SHUTDOWNHOOK NON FUNZIONA SU NETBEANS, NECESSITO DI USARE UN METODO ALTERNATIVO 
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
    // private static AtomicBoolean shutdown = new AtomicBoolean(false);

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
            int total_users = 0;
            reader.beginArray();
            while (reader.hasNext()) {
                String username = "", password = "";
                int total_games_played = 0, total_games_won = 0, current_winstreak = 0, longest_winstreak = 0;
                boolean has_played = false;
                ArrayList<Integer> guess_distribution = new ArrayList<>();

                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if ("username".equals(key)) {
                        username = reader.nextString();
                    } else if ("password".equals(key)) {
                        password = reader.nextString();
                    } else if ("total_played_games".equals(key)) {
                        total_games_played = reader.nextInt();
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
                total_users++;
                User user = new User(username, password, total_games_played, total_games_won, current_winstreak, longest_winstreak, has_played, guess_distribution);
                users.put(username, user);
            }
            reader.endArray();
            System.out.println("[DEBUG] file " + USER_DB + " caricato con successo. (" + total_users + " utenti)");
            // Stampa mappa per debug
            // users.forEach((k,v)-> System.out.println("key: "+k+", value: "+v));
        } catch (FileNotFoundException ex) {
            System.err.println("Errore, file '" + USER_DB + "' non trovato.");
            ex.printStackTrace();
        } catch (IOException ex) {
            System.err.println("Errore durante la lettura del file " + USER_DB);
            ex.printStackTrace();
        }
    }

    private static void saveUsersToJSON() {
        try (JsonWriter writer = new JsonWriter(new FileWriter(USER_DB))) {
            int total_users = 0;
            writer.setIndent("\t");
            writer.beginArray();
            for (String username : users.keySet()) {
                User userObj = users.get(username);

                writer.beginObject();
                writer.name("username").value(userObj.getUsername());
                writer.name("password").value(userObj.getPassword());
                writer.name("total_played_games").value(userObj.getTotal_played_games());
                writer.name("total_games_won").value(userObj.getTotal_games_won());
                writer.name("current_winstreak").value(userObj.getCurrent_winstreak());
                writer.name("longest_winstreak").value(userObj.getLongest_winstreak());
                writer.name("has_played").value(userObj.Has_played());
                writer.name("guess_distribution");
                writer.beginArray();
                for (int guess : userObj.getGuess_distribution()) {
                    writer.value(guess);
                }
                writer.endArray();
                writer.endObject();
                total_users++;
            }
            writer.endArray();
            System.out.println("[DEBUG] file " + USER_DB + " scritto con successo. (" + total_users + " utenti)");
        } catch (IOException ex) {
            System.err.println("Errore nella scrittura del file " + USER_DB);
            ex.printStackTrace();
        }
    }

    /**
     * Aggiunge un nuovo utente alla mappa <Utente, Username>
     *
     * @param username il nome utente del nuovo utente
     * @param password la password del nuovo utente
     * @return una stringa che indica l'esito dell'operazione
     */
    public static String addUser(String username, String password) {
        if (password.equals("")) {
            return "EMPTY";
        } else if (users.containsKey(username)) {
            return "DUPLICATE";
        }

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

            // task di shutdown
            Thread shutdownThread = new Thread() {
                public void run() {
                    // alternativa allo shutdownhook, che tramite la console netbeans appare non funzionare.
                    try (Scanner signalInput = new Scanner(System.in)) {
                        if (signalInput.hasNext()) {
                            if (signalInput.next().equals("t")) {
                                // shutdown.set(true);

                                // salvo gli utenti
                                saveUsersToJSON();

                                // Avvio la procedura di terminazione del server.
                                System.out.println("[SERVER] Avvio terminazione...");
                                // Chiudo la ServerSocket in modo tale da non accettare piu' nuove richieste.
                                try {
                                    welcomeSocket.close();
                                } catch (IOException e) {
                                    System.err.printf("[SERVER] Errore: %s\n", e.getMessage());
                                }
                                // Faccio terminare il pool di thread.
                                pool.shutdown();
                                try {
                                    if (!pool.awaitTermination(TIMEOUT, TimeUnit.MILLISECONDS)) {
                                        pool.shutdownNow();
                                    }
                                } catch (InterruptedException e) {
                                    pool.shutdownNow();
                                }
                                System.out.println("[SERVER] Terminato.");
                            }
                        };
                    }
                }
            };
            shutdownThread.start();
            /* Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    System.out.println("Running Shutdown Hook");
                }
            }); */ // Avvio l'handler di terminazione.
            //Runtime.getRuntime().addShutdownHook(new TerminationHandler(TIMEOUT, pool, welcomeSocket));
            // Quando il TerminationHandler chiude la ServerSocket viene sollevata una SocketException ed esco dal ciclo.
            while (true) {
                pool.execute(new ClientHandler(welcomeSocket.accept(), connectedUsers++));
            }
        } catch (SocketException se) {
        } catch (IOException ex) {
            System.err.printf("[SERVER] Errore: %s\n", ex.getMessage());
            ex.printStackTrace();
        }
    }
}
