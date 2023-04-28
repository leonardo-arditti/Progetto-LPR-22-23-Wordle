
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author Leonardo Arditti 23/4/2023
 */
public class WordleClient {

    // Percorso del file di configurazione del client
    public static final String CONFIG = "src/client.properties";

    // Nome host e porta del server.
    public static String HOSTNAME;
    public static int PORT;

    // gruppo di multicast e porta da usare per la MulticastSocket
    public static String MULTICAST_GROUP_ADDRESS;
    public static int MULTICAST_GROUP_PORT;
    public static int SERVER_NOTIFICATION_PORT; // porta usata dal client per inviare al server i resoconti delle proprie partite usando UDP
    private static MulticastSocket multicastSocket; // socket su cui il client riceve i messaggi da un gruppo di multicast
    private static InetAddress group;
    private static ExecutorService multicast_pool;

    private static Socket clientSocket;
    private static PrintWriter out;
    private static Scanner in;

    private static User currentUser = new User(); // inizialmente un placeholder, poi sostituito dall'utente corrispondente a quello specificato al login
    private static boolean logged_out = false; // variabile aggiornata al logout, comporta la terminazione del programma
    private static boolean game_started = false; // per impedire che si possa invocare sendWord senza aver invocato prima playWORDLE()
    private static boolean game_finished = false; // per consentire la condivisione dei tentativi per l'ultima partita giocata solo a partita finita
    private static boolean has_won = false; // variabile per il messaggio da condividere al gruppo multicast che verrà mandato al server, che deve includere oltre ai tentativi se l'utente ha vinto o meno

    private static ArrayList<String> notifications = new ArrayList<>(); // notifiche inviate dal server riguardo alle partite di altri utenti
    private static ArrayList<String> userGuessesCodified; // tentativi dell'utente per la partita in corso (codificati perchè non mostro le lettere ma i simboli '?','X','+' associati ai colori)

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
            SERVER_NOTIFICATION_PORT = Integer.parseInt(prop.getProperty("SERVER_NOTIFICATION_PORT"));
            MULTICAST_GROUP_ADDRESS = prop.getProperty("MULTICAST_GROUP_ADDRESS");
            MULTICAST_GROUP_PORT = Integer.parseInt(prop.getProperty("MULTICAST_GROUP_PORT"));
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

                // dopo la fase di login (in caso di successo), ogni client si unisce a un gruppo di multicast di cui fa parte anche il server
                joinMulticastGroup();
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
                leaveMulticastGroup();
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
                game_finished = false;
                currentUser.setHas_played();
                userGuessesCodified = new ArrayList<>(); // azzero le guess word inviate dell'utente e memorizzate
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
            game_finished = true;

            has_won = true;
            userGuessesCodified.add("[+, +, +, +, +, +, +, +, +, +]"); // codifica equivalente a aver indovinato la parola segreta
            // chiedo all'utente se vuole condividere l'esito della partita e i tentativi
            System.out.println("Vuoi condividere il risultato della partita? Puoi farlo usando il comando share() !");
        } else if (response.startsWith("LOSE")) {
            System.out.println(response);
            game_started = false;
            game_finished = true;

            has_won = false;
            // estraggo tentativo dalla risposta
            System.out.println("Vuoi condividere il risultato della partita? Puoi farlo usando il comando share() !");
            userGuessesCodified.add(extractAttemptFromResponse(response));
        } else if (response.startsWith("CLUE")) {
            System.out.println(response);
            // estraggo tentativo dalla risposta
            userGuessesCodified.add(extractAttemptFromResponse(response));
        }
    }

    // per estrarre il tentativo da stringhe come "CLUE: [?, X, X, X, X, ?, ?, X, X, ?], hai a disposizione 11 tentativi."
    public static String extractAttemptFromResponse(String response) {
        return response.substring(response.indexOf("["), response.indexOf("]") + 1);
    }

    public static void help() {
        System.out.println("I comandi disponibili sono:\n"
                + "login(username, password)\n"
                + "logout(username)\n"
                + "playWORDLE()\n"
                + "sendWord(guessWord)\n"
                + "sendMeStatistics()\n"
                + "share()\n"
                + "showMeSharing()\n"
                + "help");
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

    public static void share(boolean malformed) {
        if (!currentUser.isLoggedIn()) {
            System.err.println("E' possibile richiedere la condivisione dei tentativi effettuati per l'ultima partita solo una volta loggati.");
            return;
        }

        if (!game_finished) {
            System.err.println("E' possibile richiedere la condivisione dei tentativi effettuati per l'ultima partita solo una volta che la partita è terminata.");
            return;
        }

        String msgToShare = "";
        msgToShare += currentUser.getUsername() + ":" + (has_won ? "WIN" : "LOSE") + ":ATTEMPTS:";
        msgToShare += "{";

        for (int i = 0; i < userGuessesCodified.size() - 1; i++) {
            msgToShare += userGuessesCodified.get(i) + ",";
        }

        msgToShare += userGuessesCodified.get(userGuessesCodified.size() - 1) + "}";

        try (DatagramSocket ds = new DatagramSocket()) {
            byte[] msg = msgToShare.getBytes();
            DatagramPacket dp = new DatagramPacket(msg, msg.length, InetAddress.getByName(HOSTNAME), SERVER_NOTIFICATION_PORT);
            ds.send(dp);
        } catch (SocketException | UnknownHostException ex) {
            System.err.println("Errore nella condivisione con il gruppo multicast.");
            ex.printStackTrace();
        }  catch (IOException ex) {
            System.err.println("Errore durante l'invio del datagram al gruppo multicast.");
        }
        
        System.out.println("Messaggio condiviso con successo con il gruppo sociale!");
    }

    // Unione al gruppo multicast, da effettuare una volta loggato con successo
    public static void joinMulticastGroup() {
        try {
            multicastSocket = new MulticastSocket(MULTICAST_GROUP_PORT);
            group = InetAddress.getByName(MULTICAST_GROUP_ADDRESS);
            multicastSocket.joinGroup(group); // mi unisco a un gruppo, identificato da un indirizzo multicast di classe D
        } catch (IOException ex) {
            System.err.println("Errore nella creazione del MulticastSocket.");
            ex.printStackTrace();
        }
        
        // System.out.println("Client aggiunto al gruppo multicast.");
        
        multicast_pool.submit(() -> {
            receiveMulticastNotifications();
        });
    }

    // Una volta fatto logout si esce dal gruppo multicast a cui ci eravamo uniti
    public static void leaveMulticastGroup() {
        try {
            multicastSocket.leaveGroup(group);
            multicastSocket.close();
        } catch (IOException ex) {
            System.err.println("Errore nell'uscita dal gruppo multicast.");
            ex.printStackTrace();
        }
    }

    public static void receiveMulticastNotifications() {
        int buff_size = 8192;
        byte[] buffer = new byte[buff_size];
        DatagramPacket notification = new DatagramPacket(buffer, buff_size);

        while (!logged_out && currentUser.isLoggedIn()) { // aggiungere controllo su logged in, altrimenti ricevo fin dall'inizio della sessione senza aver fatto login 
            try {
                // ricevo notifiche inviate dal server riguardo alle partite degli altri utenti
                multicastSocket.receive(notification);

                //TODO: va controllato se la notifica è di una partita che non sia di questo host (formato stringa: username:[..])
                // (perchè vanno ricevute "le notifiche di partite di altri utenti")
                String notification_msg = new String(notification.getData(), 0, notification.getLength(), "UTF-8");
                if (!notification_msg.startsWith(currentUser.getUsername())) { // aggiungo solo le notifiche che non sono inviate dal client corrente
                    notifications.add(notification_msg);
                    System.out.println("[NUOVA NOTIFICA RICEVUTA!] Per leggerla, usa showMeSharing().");
                }
            } catch (IOException ex) {
                // System.err.println("Errore nella ricezione delle notifiche riguardo le partite degli altri utenti.");
                // ex.printStackTrace();
            }
        }
    }
    
    public static void showMeSharing(boolean malformed) {
        if (!currentUser.isLoggedIn()) {
            System.err.println("E' possibile vedere le notifiche inviate dal server riguardo le partite degli altri utenti solo una volta loggati.");
            return;
        }
        
        if (notifications.size() == 0) {
            System.err.println("Nessuna notifica da visualizzare riguardo alle partite degli altri utenti.");
            return;
        }
        
        int index = 0;
        System.out.println("=====NOTIFICHE=====");
        for (String notification : notifications) {
            System.out.print("["+index+"]");
            System.out.println(notification);
            index++;
        }
        System.out.println("===================");
        
        // una volta viste le notifiche le cancello
        notifications.clear();
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
                if ("".equals(username)) {
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
                String guessWord = "";
                if (!(command.contains("(") && command.contains(")")))
                    malformed = true;
                else 
                    guessWord = parts[1].substring(0, parts[1].length() - 1);
                
                if ("".equals(guessWord))
                    malformed = true;
                
                sendWord(guessWord, malformed);
                break;

            case "sendMeStatistics":
                if (!command.equals("sendMeStatistics()")) {
                    malformed = true;
                }
                sendMeStatistics(malformed);
                break;

            case "share":
                if (!command.endsWith("()")) {
                    malformed = true;
                }

                share(malformed);
                break;

            case "showMeSharing":
                if (!command.endsWith("()")) {
                    malformed = true;
                }
                
                showMeSharing(malformed);
                break;

            case "help":
                help();
                break;

            default:
                System.err.printf("Comando %s non riconosciuto, riprovare.\r\n", commandName);
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
            
            try {
                if (!multicast_pool.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                    multicast_pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                multicast_pool.shutdownNow();
            }

        } catch (IOException ex) {
            System.err.println("Errore nella chiusura delle socket e/o nel rilascio delle risorse associate agli stream e/o nella chiusura del thread pool.");
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
        multicast_pool = Executors.newFixedThreadPool(1);

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