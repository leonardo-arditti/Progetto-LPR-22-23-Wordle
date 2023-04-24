
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Properties;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Leonardo Arditti 23/4/2023
 */
public class WordleClient {

    // Percorso del file di configurazione del client
    public static final String CONFIG = "client.properties";

    // Nome host e porta del server.
    public static String HOSTNAME;
    public static int PORT;
    public static int TIMEOUT;

    private static Socket clientSocket;
    private static PrintWriter out;
    private static BufferedReader in;

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
            TIMEOUT = Integer.parseInt(prop.getProperty("TIMEOUT"));
        } catch (IOException ex) {
            System.err.println("Errore durante la lettura del file di configurazione.");
        }
    }

    private static void register(String username, String password) {

    }

    private static void login(String username, String password) {

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

            default:
                System.out.printf("Comando %s non riconosciuto, riprovare\r\n");
        }
    }

    private static void startConnection() {
        try {
            clientSocket = new Socket(HOSTNAME, PORT);
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        } catch (IOException ex) {
            System.err.println("Errore nella connessione al server.");
        }
    }

    private static void closeConnection() {
        try {
            clientSocket.close();
            in.close();
            out.close();
        } catch (IOException ex) {
            System.err.println("Errore nella chiusura del socket e/o nel rilascio delle risorse associate agli stream.");
        }
    }

    public static void main(String args[]) {
        try {
            // Lettura del file di configurazione del client
            readConfig();
        } catch (IOException ex) {
            System.err.println("Errore nella lettura del file di configurazione.");
        }

        Scanner userInput = new Scanner(System.in);

        System.out.println("Benvenuto a Wordle! ");
        while (true) {
            String cmd = userInput.nextLine();

            handleCmd(cmd);
        }
    }
}
