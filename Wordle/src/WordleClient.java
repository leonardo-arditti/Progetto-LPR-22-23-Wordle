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
    
    private static User user;
    private static boolean logged_out = false;
    
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

    private static void register(String username, String password) {
        
        if (user.isLoggedIn()) {
            // Non consento a un utente già loggato di registrarsi nuovamente
            System.err.println("Impossibile registrarsi nuovamente una volta loggati.");
            return;
        }
        
        // Uso la virgola come delimitatore
        out.println("REGISTER"+","+username+","+password);
        String response = in.nextLine();
        
        switch(response) {
            case "SUCCESS":
                System.out.println("Registrazione avvenuta con successo. Procedere con il login.");
                break;
                
            case "DUPLICATE":
                System.err.println("Errore, l'username scelto per la registrazione è già stato utilizzato.");
                break;
                
            case "EMPTY":
                System.err.println("Errore, la password non può essere vuota.");
                break;
        }
    }

    private static void login(String username, String password) {
        
        if (user.isLoggedIn()) {
            // Non consento a un utente già loggato di loggarsi nuovamente
            System.err.println("Impossibile loggarsi nuovamente una volta loggati.");
            return;
        }
        
        out.println("LOGIN"+","+username+","+password);
        String response = in.nextLine();
        
        switch (response){
            case "SUCCESS":
                System.out.println("Bentornato "+username+"!");
                user = new User(username, password);
                user.setLoggedIn();
                break;
                
            case "WRONG_CREDENTIALS":
                System.err.println("Username/password scorretti, riprovare.");
                
        }
    }

    private static void handleCmd(String cmd) {
        String[] parts = cmd.split("\\("); // Divide la stringa in base alla parentesi aperta
        String commandName = parts[0]; // Il nome del comando è la prima sottostringa
        String credentials;
        String username;
        String password;

        switch (commandName) {
            case "register":
                credentials = parts[1].substring(0, parts[1].length() - 1); // Estrae le credenziali dal resto della stringa, rimuovendo la parentesi chiusa finale
                String[] registerParts = credentials.split(","); // Divide le credenziali in base alla virgola
                username = registerParts[0]; // Il primo elemento è il nome utente
                password = registerParts[1]; // Il secondo elemento è la password
                System.out.println(username + " " + password);
                register(username, password);
                break;

            case "login":
                credentials = parts[1].substring(0, parts[1].length() - 1); // Estrae le credenziali dal resto della stringa, rimuovendo la parentesi chiusa finale
                String[] loginParts = credentials.split(","); // Divide le credenziali in base alla virgola
                username = loginParts[0]; // Il primo elemento è il nome utente
                password = loginParts[1]; // Il secondo elemento è la password
                login(username, password);
                break;

            case "playWORDLE":
                break;

            case "sendWord":
                break;

            case "sendMeStatistics":
                break;

            case "share":
                break;

            case "showMeSharing":
                break;

            case "help":
                break;
                
            default:
                System.out.printf("Comando %s non riconosciuto, riprovare\r\n", commandName);
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

        System.out.println("Benvenuto su Wordle! Digita help per una lista dei comandi disponbili.");
        while (!logged_out) {
            System.out.print(">");
            String cmd = userInput.nextLine();

            handleCmd(cmd);
        }
    }
}
