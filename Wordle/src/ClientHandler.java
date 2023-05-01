import java.io.PrintWriter;
import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;

/**
 * @author Leonardo Arditti 24/4/2023
 */

// Task che poi sarà eseguito da un thrad per gestire la comunicazione con un client
public class ClientHandler implements Runnable {

    private int client_id; // per poter identificare il client connesso nelle stampe sulla CLI del server
    private final Socket socket; // socket per lo scambio di dati con il client attraverso la rete
    private boolean is_playing = false; // usato per gestire il logout una volta iniziata una partita 
    private boolean has_won = false;  // usato per impedire il proseguo di una partita quando si è conclusa perchè indovinata la secret word
    // private boolean has_shared = false; // da rimuovere? non ha senso limitare il numero di volte che si condivide.. wordle vero non lo fa!
    private User connectedUser; // inizialmente un utente fittizio, dopo il login sarà l'utente autenticato
    private boolean logged_out = false; // flag per andare a terminare la comunicazione con il client una volta effettuato il logout

    private int userAttempts; // ogni tentativo da parte dell'utente di indovinare la secret word comporta un incremento del contatore
    private final int MAX_ATTEMPTS = 12;
    private String secretWord; // la parola segreta che il client deve indovinare
    
    public ClientHandler(Socket socket, int client_id) {
        this.socket = socket;
        this.client_id = client_id;
    }

    /**
     * Metodo che gestisce la comunicazione (cioè i comandi inviati da parte del client), fornendo risposte diverse a seconda del comando ricevuto.
     */
    @Override
    public void run() {
        try (Scanner in = new Scanner(socket.getInputStream());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);) {

            System.out.println("[Client #" + client_id + "] ha effettuato una richiesta di connessione da " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort());

            while (in.hasNextLine() && !logged_out) { // una volta che effettuo il logout esco dal ciclo di elaborazione dei comandi del client
                String cmd = in.next(); // comando nella sua forma completa, e.g: REGISTER,username,password (nota: la sintassi non è quella dei comandi inviati dal client tramite CLI ma una versione che ne semplifica il parsing essendo priva di parentesi tonde)

                String[] cmd_components = cmd.split(",");
                System.out.println("[Client #" + client_id + "] comando ricevuto: " + cmd);
                String op = cmd_components[0]; // comando ricevuto (e.g: REGISTER, LOGIN, LOGOUT, ...)
                String username, password;

                switch (op) { // La gestione del comando varia a seconda del comando ricevuto
                    case "REGISTER":
                        username = cmd_components[1];
                        password = cmd_components[2];
                        
                        // Aggiungo il client agli utenti memorizzati dal server (se non presente un utente con lo stesso nome e se la password non è vuota)
                        String outcome = WordleServer.addUser(username, password);
                        out.println(outcome); // invio al client la risposta del server
                        break;

                    case "LOGIN":
                        username = cmd_components[1];
                        password = cmd_components[2];
                        User matchedUser = WordleServer.getUser(username); // Esiste un utente con l'username fornito?
                        if (matchedUser == null) { // Se non esiste => errore (cioè comunico l'errore al client)
                            out.println("NON_EXISTING_USER"); // comunico esito all'utente
                        } else if (!matchedUser.getPassword().equals(password)) { // Esiste ma la password ricevuta dal client e quella memorizzata non coincidono => errore
                            out.println("WRONG_PASSWORD");
                        } else if (matchedUser.isLoggedIn()) { // Esiste ma l'utente con quell'username è già autenticato (non ammetto sessioni multiple per uno stesso utente) => errore
                            out.println("ALREADY_LOGGED");
                        } else { // Autenticazione effettuata con successo
                            out.println("SUCCESS");
                            connectedUser = matchedUser; // Recupero le informazioni sull'utente autenticato dagli utenti memorizzati
                            connectedUser.setLoggedIn(); // Imposto lo stato di autenticazione dell'utente 
                            WordleServer.updateUser(connectedUser); // Aggiorno l'utente ora autenticato negli utenti memorizzati
                        }
                        break;

                    case "LOGOUT":
                        username = cmd_components[1];
                        if (!connectedUser.getUsername().equals(username)) { // L'utente specificato dal client nel logout non coincide con l'username di chi ha fatto la richiesta
                            out.println("ERROR"); // comunico esito all'utente
                        } else {
                            connectedUser.setNotLoggedIn(); // Imposto lo stato di autenticazione dell'utente..
                            WordleServer.updateUser(connectedUser); //.. e lo aggiorno negli utenti memorizzati
                            out.println("SUCCESS");
                            logged_out = true; // per interrompere il ciclo di gestione dei comandi ricevuti dal client

                            if (is_playing) {
                                // Se decido di fare logout una volta iniziata una partita allora la partita non conclusa è considerata persa.
                                // L'esito negativo della partita è trasparente all'utente, 
                                // ovvero non è mandato un messaggio di notifica per la terminazione prematura della partita ma solo che il logout è avvenuto con successo
                                connectedUser.addLose(); // Aggiungo una sconfitta all'utente
                                WordleServer.updateUser(connectedUser); // Aggiorno l'utente negli utenti memorizzati
                            }
                        }
                        break;

                    case "PLAYWORDLE":
                        // NB: login dell'utente controllato da parte del client
                        outcome = WordleServer.checkIfUserHasPlayed(connectedUser.getUsername()); // Controllo se l'utente ha già provato a giocare con l'ultima secret word estratta
                        out.println(outcome);
                        if (outcome.equals("SUCCESS")) {
                            secretWord = WordleServer.getSecretWord();
                            is_playing = true;
                            has_won = false; // necessario se si proviene da una partita precedente che è stata vinta al fine di poter inviare i propri tentativi con sendWord
                            userAttempts = 0; // ogni partita azzera il numero dei tentativi effettuati in precedenti partite del giocatore
                        }
                        break;

                    case "SENDWORD":
                        String guess = cmd_components[1];

                        if (has_won) { // se l'utente ha vinto (indovinato l'ultima secret word) e non ha richiesto di giocare una nuova partita => errore (non può sottomettere una nuova guessed word)
                            out.println("ALREADY_WON"); // comunico esito all'utente
                        } else if (userAttempts == MAX_ATTEMPTS) { // l'utente ha raggiunto il numero massimo di tentativi senza indovinare la parola (ha perso)
                            out.println("MAX_ATTEMPTS");
                        } else if (!WordleServer.isInVocabulary(guess)) { // la parola mandata dal client non è nel vocabolario, tentativo non contato
                            out.println("NOT_IN_VOCABULARY");
                        } else {
                            // parola nel vocabolario, conto il tentativo
                            userAttempts++;
                            
                            if (secretWord.equals(guess)) { // l'utente ha indovinato la parola segreta
                                out.println("WIN: Hai indovinato la secret word in " + userAttempts + " tentativi!");
                                connectedUser.addWin(userAttempts); // aggiorno statistiche dell'utente con una vittoria
                                WordleServer.updateUser(connectedUser); // aggiorno utente nella struttura degli utenti memorizzati
                                has_won = true;
                                is_playing = false; // non gioca più, la partita è finita (è importante sapere se al momento del logout la partita è finita o è in corso, nell'ultimo caso è contata come persa)
                            } else {
                                String clue = provideClue(guess); // calcolo dei suggerimenti in base alla parola fornita
                                
                                if (userAttempts == MAX_ATTEMPTS) { // se tentativi finiti per indovinare la secret word 
                                    out.println("LOSE: " + clue + ", la secret word era " + secretWord + ". Grazie per aver giocato!");
                                    connectedUser.addLose(); // aggiorno statistiche dell'utente con una sconfitta
                                    WordleServer.updateUser(connectedUser); // aggiorno utente nella struttura degli utenti memorizzati
                                    is_playing = false; // non gioca più, la partita è finita
                                } else {
                                    // tentativi non finiti per indovinare la secret word, invio dei suggerimenti sulla base della parola fornita
                                    out.println("CLUE: " + clue + ", hai a disposizione " + (MAX_ATTEMPTS - userAttempts) + " tentativi.");
                                }
                            }
                        }
                        break;
                        
                    case "SENDMESTATISTICS": 
                        outcome = WordleServer.getUser(connectedUser.getUsername()).statistics(); // recupero le statistiche dell'utente dalla struttura degli utenti memorizzati
                        out.println(outcome); // invio le statistiche all'utente
                        break;
                }
            }
            System.out.println("[Client #" + client_id + "] disconnesso dal server.");
            
            // chiusura socket e stream associati alla socket
            socket.close();
            in.close();
            out.close();
        } catch (Exception e) {
            System.err.println("Errore nella comunicazione con il client.");
            e.printStackTrace();
        }
    }

    /**
     * Metodo che data una guessed word dell'utente va a fornire degli indizi
     * @param  guessWord La parola proposta da parte dell'utente per indovinare la secret word
     * @return Una stringa con codificati gli indizi relativi alla guessed word fornita
     */
    public String provideClue(String guessWord) {
        /* Legenda:
         * GRIGIO = X, VERDE = +, GIALLO = ?
         * GRIGIO : lettera non appartenente alla parola segreta
         * VERDE  : lettera appartenente alla parola segreta e in posizione corretta
         * GIALLO : lettera appartenente alla parola segreta ma in posizione sbagliata
         */

        char[] guessWord_CA = guessWord.toCharArray(); // converto guessWord in array di char per semplificarne la manipolazione..
        char[] secretWord_CA = secretWord.toCharArray();//..analogamente per la secret word...
        char[] clue_CA = new char[10];//...e per gli indizi

        for (int i = 0; i < guessWord.length(); i++) {
            if (guessWord_CA[i] == secretWord_CA[i]) { // lettera appartenente alla parola segreta e in posizione corretta
                clue_CA[i] = '+';
            } else if (secretWord.indexOf(guessWord_CA[i]) != -1) { // lettera presente nella parola, ma in posizione sbagliata 
                clue_CA[i] = '?';
            } else { // lettera non appartenente alla parola segreta
                clue_CA[i] = 'X';
            }
        }

        return Arrays.toString(clue_CA);
    }
}
