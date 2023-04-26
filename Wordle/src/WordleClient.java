
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Properties;
import java.util.Scanner;

/**
 * @author Leonardo Arditti 23/4/2023
 */
public class WordleClient {

    // Percorso del file di configurazione del client
    public static final String CONFIG = "src/client.properties";

    // Nome host e porta del server.
    public static String HOSTNAME;
    public static int PORT;

    private static Socket clientSocket;
    private static PrintWriter out;
    private static Scanner in;

    private static User currentUser = new User(); // inizialmente un placeholder, poi sostituito dall'utente corrispondente a quello specificato al login
    private static boolean logged_out = false; // da aggiornare in logout, comporta la terminazione del programma
    private static boolean game_started = false; // per impedire che si possa invocare sendWord senza aver invocato prima playWORDLE()

    /**
     * Metodo che legge il file di configurazione del client
     *
     * @throws FileNotFoundException se il file non esiste
     * @throws IOException se si verifica un errore durante la lettura
     */
    public static void readConfig() throws FileNotFoundException, IOException {
        try (InputStream input = new FileInputStream(CONFIG)) {
            Properties prop = new Properties();
            prop.load(input);
            HOSTNAME = prop.getProperty("HOSTNAME");
            PORT = Integer.parseInt(prop.getProperty("PORT"));
        } catch (IOException ex) {
            System.err.println("Errore durante la lettura del file di configurazione.");
            ex.printStackTrace();
        }
    }

    private static void register(String username, String password, boolean malformed) {
        if (malformed) {
            System.err.println("Comando malformato, riprovare.");
            return;
        }

        if (currentUser.isLoggedIn()) {
            // Non consento a un utente già loggato di registrarsi nuovamente
            System.err.println("Impossibile registrarsi nuovamente una volta loggati.");
            return;
        }

        // Uso la virgola come delimitatore tra i vari campi di un comando, semplifica il parsing lato server
        out.println("REGISTER" + "," + username + "," + password);
        String response = in.nextLine();

        switch (response) {
            case "SUCCESS":
                System.out.println("Registrazione avvenuta con successo. Procedere con il login usando login(username,password).");
                break;

            case "DUPLICATE":
                System.err.println("Errore, l'username scelto per la registrazione è già stato registrato.");
                break;

            case "EMPTY":
                System.err.println("Errore, la password non può essere vuota.");
                break;
        }
    }

    private static void login(String username, String password, boolean malformed) {
        if (malformed) {
            System.err.println("Comando malformato, riprovare.");
            return;
        }

        if (currentUser.isLoggedIn()) {
            // Non consento a un utente già loggato di loggarsi nuovamente
            System.err.println("Impossibile loggarsi nuovamente una volta loggati.");
            return;
        }

        out.println("LOGIN" + "," + username + "," + password);
        String response = in.nextLine();

        switch (response) {
            case "SUCCESS":
                System.out.println("Bentornato " + username + "! Login avvenuto con successo.");
                currentUser = new User(username, password);
                currentUser.setLoggedIn();
                break;

            case "NON_EXISTING_USER":
                System.err.println("L'username specificato non corrisponde a un utente registrato, riprovare.");
                break;

            case "WRONG_PASSWORD":
                System.err.println("Password scorretta, riprovare.");
                break;

            case "ALREADY_LOGGED":
                System.err.println("L'utente " + username + " è già loggato.");
                break;
        }
    }

    private static void logout(String username, boolean malformed) {
        if (malformed) {
            System.err.println("Comando malformato, riprovare.");
            return;
        }

        if (!currentUser.isLoggedIn()) {
            System.err.println("E' possibile fare il logout solo una volta loggati.");
            return;
        }

        out.println("LOGOUT" + "," + username);
        String response = in.nextLine();

        switch (response) {
            case "ERROR":
                System.err.println("Errore nell'operazione richiesta, riprovare.");
                break;

            case "SUCCESS":
                currentUser = new User();
                logged_out = true;
                System.out.println("Disconnessione avvenuta con successo, uscita dal programma in corso. A presto!");
                break;
        }
    }

    public static void playWORDLE(boolean malformed) {
        if (malformed) {
            System.err.println("Comando malformato, riprovare.");
            return;
        }

        if (!currentUser.isLoggedIn()) {
            System.err.println("E' possibile giocare solo una volta loggati.");
            return;
        }

        out.println("PLAYWORDLE");
        String response = in.nextLine();

        switch (response) {
            case "ALREADY_PLAYED":
                System.err.println("Hai già giocato con l'ultima parola estratta dal server o la stai giocando attualmente.");
                break;

            case "SUCCESS":
                System.out.println("Inizio della sessione di gioco, usare sendWord(<guessWord>) per giocare, hai a disposizione 12 tentativi.");
                game_started = true;
                currentUser.setHas_played();
                break;
        }
    }

    public static void sendWord(String guessWord, boolean malformed) {
        if (malformed) {
            System.err.println("Comando malformato, riprovare.");
            return;
        }

        if (!currentUser.isLoggedIn()) {
            System.err.println("E' possibile inviare una parola solo una volta loggati.");
            return;
        }

        if (!game_started) {
            System.err.println("E' possibile inviare una guess word solo dopo aver iniziato una partita (con playWORDLE())");
            return;
        }

        out.println("SENDWORD" + "," + guessWord);
        String response = in.nextLine();

        if (response.equals("NOT_IN_VOCABULARY")) {
            System.err.println("La parola inviata non appartiene al vocabolario del gioco, riprovare.");
        } else if (response.equals("MAX_ATTEMPTS")) {
            System.err.println("E' stato raggiunto il numero massimo di tentativi per indovinare la secret word corrente.");
            game_started = false;
        } else if (response.equals("ALREADY_WON")) {
            System.err.println("La secret word è stata già indovinata. Attendere la pubblicazione della nuova secret word.");
            game_started = false;
        } else if (response.startsWith("WIN")) {
            System.out.println(response);
            game_started = false;
        } else if (response.startsWith("LOSE")) {
            System.err.println(response);
            game_started = false;
        } else if (response.startsWith("CLUE")) {
            System.out.println(response);
        }
    }

    public static void help() {
        System.out.println("I comandi disponibili sono:\n"
                + "login(username, password)\n"
                + "logout(username)\n"
                + "playWORDLE()\n"
                + "sendWord(guessWord)\n"
                + "sendMeStatistics()\n"
                + "share()\n"
                + "showMeSharing()");
    }

    public static void sendMeStatistics(boolean malformed) {
        if (malformed) {
            System.err.println("Comando malformato, riprovare.");
            return;
        }

        if (!currentUser.isLoggedIn()) {
            System.err.println("E' possibile richiedere le statistiche solo una volta loggati.");
            return;
        }

        out.println("SENDMESTATISTICS");
        String response = in.nextLine();

        System.out.println("=====STATISTICHE=====");
        for (String component : response.split("-")) {
            System.out.println(component);
        }
        System.out.println("=====================");
    }

    private static void handleCommand(String command) {
        String[] parts = command.split("\\("); // Divide la stringa in base alla parentesi aperta
        String commandName = parts[0]; // Il nome del comando è la prima sottostringa
        String credentials;
        String username = "";
        String password = "";
        boolean malformed = false;

        switch (commandName) {
            case "register":
                credentials = parts[1].substring(0, parts[1].length() - 1); // Estrae le credenziali dal resto della stringa, rimuovendo la parentesi chiusa finale
                String[] registerParts = credentials.split(","); // Divide le credenziali in base alla virgola
                if (registerParts.length != 2) {
                    malformed = true;
                } else {
                    username = registerParts[0]; // Il primo elemento è il nome utente
                    password = registerParts[1]; // Il secondo elemento è la password
                }
                // System.out.println(username + " " + password);
                register(username, password, malformed);
                break;

            case "login":
                credentials = parts[1].substring(0, parts[1].length() - 1); // Estrae le credenziali dal resto della stringa, rimuovendo la parentesi chiusa finale
                String[] loginParts = credentials.split(","); // Divide le credenziali in base alla virgola
                if (loginParts.length != 2) {
                    malformed = true;
                } else {
                    username = loginParts[0]; // Il primo elemento è il nome utente
                    password = loginParts[1]; // Il secondo elemento è la password
                }
                login(username, password, malformed);
                break;

            case "logout":
                username = parts[1].substring(0, parts[1].length() - 1);
                if (username == "") {
                    malformed = true;
                }
                logout(username, malformed);
                break;

            case "playWORDLE":
                if (!command.equals("playWORDLE()")) { // comando non rispetta la sintassi playWORDLE();
                    malformed = true;
                }
                playWORDLE(malformed);
                break;

            case "sendWord":
                String guessWord = parts[1].substring(0, parts[1].length() - 1);
                if (guessWord == null) {
                    malformed = true;
                }
                sendWord(guessWord, malformed);
                break;

            case "sendMeStatistics":
                if (!command.equals("sendMeStatistics()")) {
                    malformed = true;
                }
                sendMeStatistics(malformed);
                break;

            case "share":
                break;

            case "showMeSharing":
                break;

            case "help":
                help();
                break;

            default:
                System.err.printf("Comando %s non riconosciuto, riprovare\r\n", commandName);
        }
    }

    private static void startConnection() {
        try {
            clientSocket = new Socket(HOSTNAME, PORT);
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new Scanner(clientSocket.getInputStream());
        } catch (IOException ex) {
            System.err.println("Errore nella connessione al server.");
            ex.printStackTrace();
        }
    }

    private static void closeConnection() {
        try {
            clientSocket.close();
            in.close();
            out.close();
        } catch (IOException ex) {
            System.err.println("Errore nella chiusura del socket e/o nel rilascio delle risorse associate agli stream.");
            ex.printStackTrace();
        }
    }

    public static void main(String args[]) {
        try {
            // Lettura del file di configurazione del client
            readConfig();
            startConnection();
        } catch (IOException ex) {
            System.err.println("Errore nella lettura del file di configurazione.");
            ex.printStackTrace();
        }

        Scanner userInput = new Scanner(System.in);

        System.out.println("[ Connesso al server " + clientSocket.getInetAddress() + " sulla porta " + clientSocket.getPort() + " ]");
        System.out.println("Benvenuto su Wordle! Digita un comando o help per una lista di tutti i comandi disponbili.");
        while (!logged_out) {
            System.out.print(">");
            String command = userInput.nextLine();

            handleCommand(command);
        }
        closeConnection();
    }
}
