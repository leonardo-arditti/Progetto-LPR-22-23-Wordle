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

    // Nome host e porta del server per comunicazione TCP
    public static String HOSTNAME;
    public static int PORT;

    // Indirizzo gruppo di multicast e porta da usare per la MulticastSocket
    public static String MULTICAST_GROUP_ADDRESS;
    public static int MULTICAST_GROUP_PORT;

    public static int SERVER_NOTIFICATION_PORT; // porta usata dal client per inviare al server i resoconti delle proprie partite usando UDP
    private static MulticastSocket multicastSocket; // socket su cui il client riceve i messaggi da un gruppo di multicast
    private static InetAddress group; // identificherà il gruppo di multicast

    // Pool di thread per andare a gestire le notifiche delle partite del gruppo sociale su multicast
    private static ExecutorService multicast_pool;

    private static Socket clientSocket;
    private static PrintWriter out;
    private static Scanner in;

    private static User currentUser = new User(); // inizialmente un placeholder (utente fittizio), poi sostituito dall'utente corrispondente a quello specificato al login
    private static boolean logged_out = false; // variabile aggiornata al logout, comporta la terminazione del programma
    
    // da valutare rimozione
    // private static boolean game_started = false; // per impedire che si possa invocare sendWord senza aver invocato prima playWORDLE()
    private static boolean game_finished = false; // per consentire la condivisione dei tentativi per l'ultima partita giocata solo a partita finita
    private static boolean has_won = false; // variabile per il messaggio da condividere al gruppo multicast che verrà mandato al server, che deve includere oltre ai tentativi se l'utente ha vinto o meno

    private static ArrayList<String> notifications = new ArrayList<>(); // notifiche inviate dal server riguardo alle partite di altri utenti
    private static ArrayList<String> userGuessesCodified; // tentativi dell'utente per la partita in corso (codificati perchè non mostro le lettere ma i simboli '?','X','+' associati ai colori)

    /**
     * Metodo che legge il file di configurazione del client.
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

    /* NB: In tutti i comandi con più componenti (e.g: register, login, logout) vado ad usare la virgola come delimitatore tra i vari campi, ciò semplifica il parsing lato server rispetto all'uso delle parentesi tonde */
    
    /**
     * Metodo che manda la richiesta di registrazione di un utente al server WORDLE
     *
     * @param username Il nome utente dell'utente da registrare
     * @param password La password dell'utente da registrare
     * @param malformed Un flag booleano che indica se il comando, nome utente o la password sono malformati. 
     *                  Se true, il metodo non eseguirà la registrazione e genererà un messaggio di errore
     */
    private static void register(String username, String password, boolean malformed) {
        // Se il comando è malformato la registrazione non viene eseguita
        if (malformed) {
            System.err.println("Comando malformato, riprovare.");
            return;
        }

        // Non consento a un utente già loggato di registrarsi nuovamente
        if (currentUser.isLoggedIn()) {
            System.err.println("Impossibile registrarsi nuovamente una volta loggati.");
            return;
        }

        out.println("REGISTER" + "," + username + "," + password); // Invio al server la richiesta di registrazione con username e password dell'utente
        String response = in.nextLine(); // recupero la risposta alla richiesta inviata

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

    /**
     * Metodo che manda la richiesta di autenticazione (login) di un utente al server WORDLE
     *
     * @param username  Il nome utente dell'utente da autenticare
     * @param password  La password dell'utente da autenticare
     * @param malformed Un flag booleano che indica se il comando, nome utente o la password sono malformati. 
     *                  Se true, il metodo non eseguirà la autenticazione e genererà un messaggio di errore
     */
    private static void login(String username, String password, boolean malformed) {
        // Se il comando è malformato la autenticazione non viene eseguita
        if (malformed) {
            System.err.println("Comando malformato, riprovare.");
            return;
        }

        // Non consento a un utente già loggato di loggarsi nuovamente
        if (currentUser.isLoggedIn()) {
            System.err.println("Impossibile loggarsi nuovamente una volta loggati.");
            return;
        }

        // Mando al server la richiesta di autenticazione al servizio e recupero la sua risposta
        out.println("LOGIN" + "," + username + "," + password);
        String response = in.nextLine();

        switch (response) {
            case "SUCCESS":
                System.out.println("Bentornato " + username + "! Login avvenuto con successo.");
                currentUser = new User(username, password); // sostituisco l'utente fittizio definito all'avvio del programma con quello associato alle credenziali di login
                currentUser.setLoggedIn(); // l'utente è ora loggato, questo consentirà di fare controlli sulle varie operazioni che richiedono una previa autenticazione (e.g: logout, playWORDLE(), sendWord, ..)

                // dopo la fase di login (in caso di successo), ogni client si unisce a un gruppo di multicast di cui fa parte anche il server
                joinMulticastGroup();
                break;

            case "NON_EXISTING_USER":
                System.err.println("L'username specificato non corrisponde a un utente registrato, riprovare.");
                break;

            case "WRONG_PASSWORD":
                System.err.println("La password specificata è scorretta, riprovare.");
                break;

            case "ALREADY_LOGGED":
                System.err.println("L'utente " + username + " è già loggato. Impossibile avere due sessioni di gioco simultanee.");
                break;
        }
    }

    /**
     * Metodo che manda la richiesta di logout di un utente al server WORDLE
     *
     * @param username  Il nome utente dell'utente da disconnettere dal servizio
     * @param malformed Un flag booleano che indica se il comando è malformato.
     *                  Se true, il metodo non eseguirà la disconnessione e genererà un messaggio di errore
     */
    private static void logout(String username, boolean malformed) {
        // Se il comando è malformato la disconnessione non viene eseguita
        if (malformed) {
            System.err.println("Comando malformato, riprovare.");
            return;
        }

        // La disconnessione viene eseguita solo per un utente che è stato precedentemente autenticato
        if (!currentUser.isLoggedIn()) {
            System.err.println("E' possibile fare il logout solo una volta loggati.");
            return;
        }

        // Mando al server la richiesta di disconnessione dal servizio e recupero la sua risposta
        out.println("LOGOUT" + "," + username);
        String response = in.nextLine();

        switch (response) {
            case "ERROR": // L'utente ha inserito nell'username un nome che non è il suo
                System.err.println("Errore nell'operazione richiesta, riprovare.");
                break;

            case "SUCCESS":
                currentUser = new User(); // Rimuovo le informazioni memorizzate dell'utente precedentemente autenticato
                leaveMulticastGroup(); // Con il logout viene lasciato il gruppo di multicast a cui l'utente si è unito al momento del login
                logged_out = true; // questo assegnamento al flag logged_out consente la uscita dal ciclo while(true) di ascolto dei comandi e la terminazione del client
                System.out.println("Disconnessione avvenuta con successo, uscita dal programma in corso. A presto!");

                break;
        }
    }

    /**
     * Metodo che manda la richiesta di inizio gioco di un utente al server WORDLE
     *
     * @param username  Il nome utente dell'utente che vuole giocare, cioè indovinare l'ultima parola estratta dal server
     * @param malformed Un flag booleano che indica se il comando è malformato.
     *                  Se true, il metodo non eseguirà la richiesta di inizio gioco e genererà un messaggio di errore
     */
    public static void playWORDLE(boolean malformed) {
        // Se il comando è malformato la richiesta di iniziare il gioco non viene inoltrata
        if (malformed) {
            System.err.println("Comando malformato, riprovare.");
            return;
        }

        if (!currentUser.isLoggedIn()) {
            System.err.println("E' possibile giocare solo una volta loggati.");
            return;
        }

        // Mando al server la richiesta di inizio gioco e recupero la sua risposta
        out.println("PLAYWORDLE");
        String response = in.nextLine();

        switch (response) {
            case "ALREADY_PLAYED":
                System.err.println("Hai già giocato con l'ultima parola estratta dal server o la stai giocando attualmente.");
                break;

            case "SUCCESS":
                System.out.println("Inizio della sessione di gioco, usare sendWord(<guessWord>) per giocare, hai a disposizione 12 tentativi.");
                // game_started = true;
                game_finished = false;
                // currentUser.setHas_played();
                userGuessesCodified = new ArrayList<>(); // azzero le guess word inviate dell'utente (in una partita precedente), se presenti
                break;
        }
    }

    /**
     * Metodo che manda la guessed word di un utente al server WORDLE
     *
     * @param guessWord La parola inviata dall'utente con lo scopo di indovinare la parola segreta (secret word)
     * @param malformed Un flag booleano che indica se il comando è malformato.
     *                  Se true, il metodo non manderà la richiesta con la guessed word e genererà un messaggio di errore
     */
    public static void sendWord(String guessWord, boolean malformed) {
        // Se il comando è malformato la guessed word specificata dall'utente non viene inoltrata
        if (malformed) {
            System.err.println("Comando malformato, riprovare.");
            return;
        }

        if (!currentUser.isLoggedIn()) {
            System.err.println("E' possibile inviare una parola solo una volta loggati.");
            return;
        }

//        if (!game_started) {
//            System.err.println("E' possibile inviare una guess word solo dopo aver iniziato una partita (con playWORDLE())");
//            return;
//        }
        // Mando al server la richiesta con la guessed word e recupero la sua risposta
        out.println("SENDWORD" + "," + guessWord);
        String response = in.nextLine();

        if (response.equals("NOT_IN_VOCABULARY")) {
            System.err.println("La parola inviata non appartiene al vocabolario del gioco, riprovare.");
        } else if (response.equals("MAX_ATTEMPTS")) {
            System.err.println("E' stato raggiunto il numero massimo di tentativi senza riuscire a indovinare la secret word corrente. Verificare la pubblicazione della nuova secret word con playWORDLE().");
            // game_started = false; // assegnamento che consente di poter inviare nuovamente il comando playWORDLE(), prima non possibile perchè la partita era iniziata
        } else if (response.equals("ALREADY_WON")) {
            System.err.println("La secret word è stata già indovinata. Verificare la pubblicazione della nuova secret word con playWORDLE().");
            // game_started = false; // assegnamento che consente di poter inviare nuovamente il comando playWORDLE(), prima non possibile perchè la partita era iniziata
        } else if (response.startsWith("WIN")) {
            System.out.println(response);
//            game_started = false;
            game_finished = true;

            has_won = true;
            userGuessesCodified.add("[+, +, +, +, +, +, +, +, +, +]"); // aggiungo ai tentativi dell'utente la codifica equivalente a aver indovinato la parola segreta

            System.out.println("Vuoi condividere il risultato della partita? Puoi farlo usando il comando share() !");
        } else if (response.startsWith("LOSE")) {
            System.out.println(response);
            // game_started = false;
            game_finished = true;
            has_won = false;

            // estraggo tentativo dalla risposta e lo aggiungo ai tentativi effettuati dall'utente per la parola segreta corrente
            userGuessesCodified.add(extractAttemptFromResponse(response));

            System.out.println("Vuoi condividere il risultato della partita? Puoi farlo usando il comando share() !");
        } else if (response.startsWith("CLUE")) {
            System.out.println(response);

            // estraggo tentativo dalla risposta e lo aggiungo ai tentativi effettuati dall'utente per la parola segreta corrente
            userGuessesCodified.add(extractAttemptFromResponse(response));
        }
    }

    /**
     * Metodo utilità per estrarre un tentativo da una risposta fornita dal server
     *
     * @param response Una stringa con la risposta fornita dal server che contiene un tentativo delimitato da parentesi quadre
     * @return Il tentativo contenuto nella stringa
     */
    public static String extractAttemptFromResponse(String response) {
        // per estrarre il tentativo da stringhe come "CLUE: [?, X, X, X, X, ?, ?, X, X, ?], hai a disposizione 11 tentativi."
        // oppure "LOSE: [+, X, X, ?, ?, X, X, ?, X, +], la secret word era azygospore. Grazie per aver giocato!" 
        return response.substring(response.indexOf("["), response.indexOf("]") + 1);
    }

    /**
     * Metodo che mostra all'utente i comandi disponibili.
     */
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

    /**
     * Metodo che manda la richiesta di visualizzare le statistiche di un utente al server WORDLE
     *
     * @param malformed Un flag booleano che indica se il comando è malformato.
     *                  Se true, il metodo non manderà la richiesta di visualizzare le statistiche e genererà un messaggio di errore
     */
    public static void sendMeStatistics(boolean malformed) {
        // Se il comando è malformato la guessed word specificata dall'utente non viene inoltrata
        if (malformed) {
            System.err.println("Comando malformato, riprovare.");
            return;
        }

        if (!currentUser.isLoggedIn()) {
            System.err.println("E' possibile richiedere le statistiche solo una volta loggati.");
            return;
        }

        // Mando al server la richiesta di visualizzazione delle statistiche e recupero la sua risposta
        out.println("SENDMESTATISTICS");
        String response = in.nextLine();

        System.out.println("=====STATISTICHE=====");
        for (String component : response.split("-")) {
            System.out.println(component);
        }
        System.out.println("=====================");
    }

    /**
     * Metodo che manda la richiesta di condividere i risultati del gioco di un utente con il gruppo sociale al server WORDLE
     *
     * @param malformed Un flag booleano che indica se il comando è malformato.
     *                  Se true, il metodo non manderà la richiesta di condividere i risultati del gioco e genererà un messaggio di errore
     */
    public static void share(boolean malformed) {
        if (!currentUser.isLoggedIn()) {
            System.err.println("E' possibile richiedere la condivisione dei tentativi effettuati per l'ultima partita solo una volta loggati.");
            return;
        }

        if (!game_finished) {
            System.err.println("E' possibile richiedere la condivisione dei tentativi effettuati per l'ultima partita solo una volta che la partita è terminata.");
            return;
        }

        // Costruzione della notifica con i risultati dell'ultima partita
        String msgToShare = "";
        msgToShare += currentUser.getUsername() + ":" + (has_won ? "WIN" : "LOSE") + ":ATTEMPTS:";
        msgToShare += "{";
        for (int i = 0; i < userGuessesCodified.size() - 1; i++) {
            msgToShare += userGuessesCodified.get(i) + ",";
        }
        msgToShare += userGuessesCodified.get(userGuessesCodified.size() - 1) + "}";

        // Creazione del datagram e invio della notifica al server usando UDP
        try (DatagramSocket ds = new DatagramSocket()) {
            byte[] msg = msgToShare.getBytes();
            DatagramPacket dp = new DatagramPacket(msg, msg.length, InetAddress.getByName(HOSTNAME), SERVER_NOTIFICATION_PORT);
            ds.send(dp);
        } catch (SocketException | UnknownHostException ex) {
            System.err.println("Errore nella condivisione con il gruppo multicast.");
            ex.printStackTrace();
        } catch (IOException ex) {
            System.err.println("Errore durante l'invio del datagram al gruppo multicast.");
        }

        System.out.println("Messaggio condiviso con successo con il gruppo sociale!");
    }

    /**
     * Metodo per l'unione al gruppo multicast, da effettuare una volta che l'utente è autenticato con successo.
     */
    public static void joinMulticastGroup() {
        try {
            multicastSocket = new MulticastSocket(MULTICAST_GROUP_PORT);
            group = InetAddress.getByName(MULTICAST_GROUP_ADDRESS);
            multicastSocket.joinGroup(group); // mi unisco a un gruppo, identificato da un indirizzo multicast di classe D
        } catch (IOException ex) {
            System.err.println("Errore nella creazione del MulticastSocket.");
            ex.printStackTrace();
        }

        // Sottometto al pool il task che sta in ascolto di notifiche multicast inviate dal server riguardo le partite degli altri utenti
        multicast_pool.submit(() -> {
            receiveMulticastNotifications();
        });
    }

    /**
     * Metodo che effettua l'uscita dal gruppo multicast a cui ci eravamo uniti al momento del login.
     */
    public static void leaveMulticastGroup() {
        try {
            multicastSocket.leaveGroup(group);
            multicastSocket.close();
        } catch (IOException ex) {
            System.err.println("Errore nell'uscita dal gruppo multicast.");
            ex.printStackTrace();
        }
    }

    /**
     * Metodo che una volta autenticato l'utente sta in attesa di nofifiche dal server riguardo alle partite degli altri utenti.
     */
    public static void receiveMulticastNotifications() {
        // Costruzione datagram per la ricezione delle notifiche
        int buff_size = 8192;
        byte[] buffer = new byte[buff_size];
        DatagramPacket notification = new DatagramPacket(buffer, buff_size);

        while (!logged_out && currentUser.isLoggedIn()) { // controllo per evitare di ricevere notifiche fin dall'inizio della sessione (prima di aver fatto login) e per uscire dal ciclo al momento del logout
            try {
                // Ricevo notifiche inviate dal server riguardo alle partite degli altri utenti
                multicastSocket.receive(notification);

                // Controllo che la notifica non sia di una partita di questo host (perchè vanno ricevute "le notifiche di partite di altri utenti")
                String notification_msg = new String(notification.getData(), 0, notification.getLength(), "UTF-8");
                if (!notification_msg.startsWith(currentUser.getUsername())) { // aggiungo solo le notifiche che non sono inviate dal client corrente
                    notifications.add(notification_msg);
                    System.out.println("[NUOVA NOTIFICA RICEVUTA!] Per leggerla, usa showMeSharing().");
                }
            } catch (IOException ex) { }
        }
    }

    /**
     * Metodo che mostra sulla CLI le notifiche inviate dal server riguardo alle partite degli altri utenti
     *
     * @param malformed Un flag booleano che indica se il comando è malformato.
     *                  Se true, il metodo non manderà la richiesta di ricevere le notifiche delle partite degli altri utenti e genererà un messaggio di errore
     */
    public static void showMeSharing(boolean malformed) {
        if (!currentUser.isLoggedIn()) {
            System.err.println("E' possibile vedere le notifiche inviate dal server riguardo le partite degli altri utenti solo una volta loggati.");
            return;
        }

        if (notifications.size() == 0) {
            System.err.println("Nessuna notifica da visualizzare riguardo alle partite degli altri utenti.");
            return;
        }

        // Visualizzazione delle notifiche ricevute
        int index = 1;
        System.out.println("=====NOTIFICHE=====");
        for (String notification : notifications) {
            System.out.print("[" + index + "]");
            System.out.println(notification);
            index++;
        }
        System.out.println("===================");

        // una volta viste le notifiche vengono cancellate, in modo da far vedere ogni volta le notifiche nuove rispetto all'invocazione precedente di showMeSharing() da parte dell'utente
        notifications.clear();
    }

    /**
     * Metodo che si occupa di gestire tutti i comandi inseriti dall'utente sulla CLI
     *
     * @param command Il comando inserito dall'utente che deve essere processato
     */
    private static void handleCommand(String command) {
        String[] parts = command.split("\\("); // Divide la stringa in base alla parentesi aperta
        String commandName = parts[0]; // Il nome del comando è la prima sottostringa
        String credentials;
        String username = "";
        String password = "";
        boolean malformed = false; // flag per indicare se il comando è malformato

        switch (commandName) { // in base al comando specificato si avranno operazioni diverse
            case "register":
                credentials = parts[1].substring(0, parts[1].length() - 1); // Estrae le credenziali dal resto della stringa (cioè parts[1], perchè parts[0] è il nome del comando), rimuovendo la parentesi chiusa finale
                String[] registerParts = credentials.split(","); // Divide le credenziali in base alla virgola
                if (registerParts.length != 2) {
                    malformed = true;
                } else {
                    username = registerParts[0]; // Il primo elemento è il nome utente
                    password = registerParts[1]; // Il secondo elemento è la password
                }
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
                username = parts[1].substring(0, parts[1].length() - 1); // estrazione dell'username
                if ("".equals(username)) { // se l'username è non specificato il comando è malformato
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
                if (!(command.contains("(") && command.contains(")"))) { // il comando è malformato se non presenta delle parentesi che lo chiudono
                    malformed = true;
                } else {
                    guessWord = parts[1].substring(0, parts[1].length() - 1); // estraggo la guessword dal comando sendWord(guessword)
                }

                if ("".equals(guessWord)) { // non posso inviare una stringa vuota come guessword, comando malformato!
                    malformed = true;
                }

                sendWord(guessWord, malformed);
                break;

            case "sendMeStatistics":
                if (!command.equals("sendMeStatistics()")) { // controllo che il comando sia ben formato
                    malformed = true;
                }
                sendMeStatistics(malformed);
                break;

            case "share":
                if (!command.equals("share()")) { // controllo che il comando sia ben formato
                    malformed = true;
                }

                share(malformed);
                break;

            case "showMeSharing":
                if (!command.endsWith("()")) { // controllo che il comando sia ben formato
                    malformed = true;
                }

                showMeSharing(malformed);
                break;

            case "help": // è stata richiesta la visualizzazione delle operazioni disponibili da parte dell'utente
                help();
                break;

            default:
                System.err.printf("Comando %s non riconosciuto, riprovare.\r\n", commandName);
        }
    }

    /**
     * Metodo che va a inizializzare la connessione al server WORDLE e gli stream associati alla socket.
     */
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

    /**
     * Metodo che va a chiudere la socket associata alla connessione con il
     * server WORDLE, gli stream ad essa associati e il pool che si occupa delle
     * notifiche multicast.
     */
    private static void closeConnection() {
        try {
            clientSocket.close();
            in.close();
            out.close();

            // Tento la chiusura graceful del pool con il task che gestisce la comunicazione multicast
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

    // Metodo main che consente di testare le funzionalità del client WORDLE
    public static void main(String args[]) {
        try {
            readConfig(); // Lettura del file properties di configurazione del client
            startConnection(); // Tenta di stabilire la connessione al server TCP WORDLE
        } catch (IOException ex) {
            System.err.println("Errore nella lettura del file di configurazione e/o inizializzazione della connessione al server WORDLE.");
            ex.printStackTrace();
        }

        Scanner userInput = new Scanner(System.in); // scanner per poter processare input da tastiera dell'utente
        multicast_pool = Executors.newFixedThreadPool(1); // pool di dimensione 1 per poter eseguire il task di ricezione multicast 

        System.out.println("[ Connesso al server " + clientSocket.getInetAddress() + " sulla porta " + clientSocket.getPort() + " ]");
        System.out.println("Benvenuto su Wordle! Digita un comando o help per una lista di tutti i comandi disponbili.");
        while (!logged_out) {
            System.out.print(">");
            String command = userInput.nextLine();

            handleCommand(command);
        }
        closeConnection(); // Tenta di chiudere la connessione stabilita precedentemente con il server TCP WORDLE
    }
}