
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
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
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
    public static int WORD_UPDATE_DELAY;

    private static String secretWord; // TODO

    private static ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();
    // private static ConcurrentHashMap<String, Boolean> usersThatHaveAlreadyPlayed = new ConcurrentHashMap<String, Boolean>(); // possibile rimozione?
    // private static AtomicBoolean shutdown = new AtomicBoolean(false);
    private static List<String> wordList = new ArrayList<>();
    private static Set<String> extractedWords = new HashSet<>();

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
            WORD_UPDATE_DELAY = Integer.parseInt(prop.getProperty("WORD_UPDATE_DELAY"));
        } catch (IOException ex) {
            System.err.println("Errore durante la lettura del file di configurazione.");
            ex.printStackTrace();
        }
    }

    public static boolean isInVocabulary(String guess) {
        // Sfrutto l'ordinamento del vocabolario e quindi che la lista di parole è anch'essa ordinata, dato che mantiene l'ordine di inserimento (se il vocabolario non fosse ordinato basterebbe fare prima Collections.sort(wordList))
        int index = Collections.binarySearch(wordList, guess);
        
        if (index >= 0)
            // parola trovata
            return true;
        else
            // parola non trovata
            return false;
    }
    
    public static String getSecretWord() {
        return secretWord;
    }

    public static void pickNewWord() {
        // Scelgo una nuova parola dal vocabolario
        Random rand = new Random();
        int index = rand.nextInt(wordList.size());

        String random_word = wordList.get(index);
        while (extractedWords.contains(random_word)) { // fino a quando la parola estratta è una parola che è stata già estratta precedentemente
            index = rand.nextInt(wordList.size());
            random_word = wordList.get(index);
        }
        
        // aggiungo la parola all'insieme della parole da non estrarre successivamente
        extractedWords.add(random_word);

        // Una volta individuata la nuova parola, aggiorno lo stato degli utenti per specificare che possono giocare
        for (Entry<String, User> entry : users.entrySet()) {
            User user = entry.getValue();
            
            user.setHas_not_played(); // imposto has_played = false per tutti gli utenti
            users.replace(user.getUsername(), user);
        }
        
        secretWord = random_word;
        System.out.println("[DEBUG] Nuova parola proposta: " + secretWord);
    }

    public static void loadVocabulary() {
        int num_words = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(VOCABULARYFILE))) {
            String line = "";

            while (line != null) {
                line = br.readLine();
                wordList.add(line);
                num_words++;
            }
        } catch (FileNotFoundException ex) {
            System.err.println("Errore, file " + VOCABULARYFILE + " non trovato, riprovare.");
            ex.printStackTrace();
        } catch (IOException ex) {
            System.err.println("Errore nel caricamento del vocabolario di parole dal file " + VOCABULARYFILE);
            ex.printStackTrace();
        }

        System.out.println("[DEBUG] Caricate con successo " + num_words + " parole nel vocabolario.");
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
                writer.name("has_played").value(false); // per consentire agli utenti di poter giocare alla prossima attivazione del server
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
    
    public static String checkIfUserHasPlayed(String username) {
        User user = users.get(username);
        if (user.Has_played())
            return "ALREADY_PLAYED";
        else {
            // Aggiorno stato del giocatore, specificando che iniziando la partita ha giocato così l'ultima parola estratta
            user.setHas_played();
            updateUser(user);
            return "SUCCESS";
        }
    }

    public static void updateUser(User user) {
        users.replace(user.getUsername(), user);
    }

    public static void main(String args[]) {
        try { 
            readConfig(); // Lettura del file di configurazione del server
            loadUsersFromJSON();
            loadVocabulary();
        } catch (IOException ex) {
            System.err.println("Errore nella lettura del file di configurazione.");
            ex.printStackTrace();
        }

        try (ServerSocket welcomeSocket = new ServerSocket(PORT)) {
            // welcomeSocket.setSoTimeout(TIMEOUT);
            System.out.println("[DEBUG] Server attivo, ascolto sulla porta " + PORT);
            ExecutorService pool = Executors.newCachedThreadPool();

            // task che propone  periodicamente  una  nuova  parola
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(() -> {
                pickNewWord();
            }, 0, WORD_UPDATE_DELAY, TimeUnit.MINUTES);
            
            // task di shutdown (alternativa allo shutdownhook, che tramite la console netbeans appare non funzionare.)
            Thread shutdownThread = new Thread() {
                public void run() {
                    try (Scanner signalInput = new Scanner(System.in)) {
                        if (signalInput.hasNext()) {
                            if (signalInput.next().equals("t")) { // t-erminate
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
                                scheduler.shutdown();
                                
                                try {
                                    if (!pool.awaitTermination(TIMEOUT, TimeUnit.MILLISECONDS)) {
                                        pool.shutdownNow();
                                    }
                                } catch (InterruptedException e) {
                                    pool.shutdownNow();
                                }
                                
                                try {
                                    if (!scheduler.awaitTermination(TIMEOUT, TimeUnit.MILLISECONDS)) {
                                        scheduler.shutdownNow();
                                    }
                                } catch (InterruptedException e) {
                                    scheduler.shutdownNow();
                                }
                                
                                System.out.println("[SERVER] Terminato.");
                            }
                        };
                    }
                }
            };
            shutdownThread.start();
            
            while (true) {
                pool.execute(new ClientHandler(welcomeSocket.accept(), connectedUsers++));
            }
        } catch (SocketException se) { } 
        catch (IOException ex) {
            System.err.printf("[SERVER] Errore: %s\n", ex.getMessage());
            ex.printStackTrace();
        }
    }
}
