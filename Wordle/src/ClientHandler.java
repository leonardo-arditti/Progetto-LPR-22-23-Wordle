import java.io.PrintWriter;
import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;

/**
 * @author Leonardo Arditti 24/4/2023
 */
public class ClientHandler implements Runnable {

    private int client_id; // per scopi di stampe
    private final Socket socket;
    private boolean is_logged = false; // possibile rimozione
    private boolean has_won = false;  // possibile rimozione
    private User connectedUser;
    private boolean logged_out = false;

    private int userAttempts = 0; // ogni tentativo da parte dell'utente di indovinare la GW comporta un incremento del contatore
    private final int MAX_ATTEMPTS = 12;
    private String secretWord;

    public ClientHandler(Socket socket, int client_id) {
        this.socket = socket;
        this.client_id = client_id;
    }

    @Override
    public void run() {
        try (Scanner in = new Scanner(socket.getInputStream());
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);) {

            System.out.println("[Client #" + client_id + "] ha effettuato una richiesta di connessione da " + socket.getInetAddress().getHostAddress());

            while (in.hasNextLine() && !logged_out) { // TODO: terminare la comunicazione una volta che il client fa logout
                String cmd = in.next(); // comando nella sua forma completa, e.g: REGISTER,username,password (nota: la sintassi non è quella dei comandi inviati dal client sulla console ma una versione che ne semplifica il parsing)

                String[] cmd_components = cmd.split(",");
                System.out.println("[Client #" + client_id + "] comando ricevuto: " + cmd);
                String op = cmd_components[0];
                String username, password;

                switch (op) {
                    case "REGISTER":
                        username = cmd_components[1];
                        password = cmd_components[2];
                        // System.out.println(username + " " + password);
                        String outcome = WordleServer.addUser(username, password);
                        out.println(outcome);
                        break;

                    case "LOGIN":
                        username = cmd_components[1];
                        password = cmd_components[2];
                        User matchedUser = WordleServer.getUser(username); // utente con nome pari a username
                        if (matchedUser == null) {
                            out.println("NON_EXISTING_USER");
                        } else if (!matchedUser.getPassword().equals(password)) {
                            out.println("WRONG_PASSWORD");
                        } else if (matchedUser.isLoggedIn()) {
                            out.println("ALREADY_LOGGED");
                        } else {
                            out.println("SUCCESS");
                            connectedUser = matchedUser;
                            connectedUser.setLoggedIn();
                            WordleServer.updateUser(connectedUser);
                        }
                        break;

                    case "LOGOUT":
                        username = cmd_components[1];
                        if (!connectedUser.getUsername().equals(username)) {
                            out.println("ERROR");
                        } else {
                            connectedUser.setNotLoggedIn();
                            WordleServer.updateUser(connectedUser);
                            out.println("SUCCESS");
                            logged_out = true;
                        }
                        break;

                    case "PLAYWORDLE":
                        // login dell'utente controllato da parte del client
                        outcome = WordleServer.checkIfUserHasPlayed(connectedUser.getUsername());
                        out.println(outcome);
                        secretWord = WordleServer.getSecretWord();
                        break;

                    case "SENDWORD":
                        String guess = cmd_components[1];
                        
                        if (userAttempts == MAX_ATTEMPTS || has_won) {
                            out.println("MAX_ATTEMPTS");
                        } else if (!WordleServer.isInVocabulary(guess)) {
                            // la parola mandata dal client non è nel vocabolario, tentativo non contato
                            out.println("NOT_IN_VOCABULARY");
                        } else {
                            // parola nel vocabolario, conto il tentativo
                            userAttempts++;

                            if (secretWord.equals(guess)) {
                                out.println("WIN: Hai indovinato la secret word in " + userAttempts + " tentativi!");
                                connectedUser.addWin(userAttempts);
                                WordleServer.updateUser(connectedUser);
                                has_won = true;
                            } else {
                                if (userAttempts == MAX_ATTEMPTS) {
                                    out.println("LOSE: La secret word era " + secretWord + ". Grazie per aver giocato!");
                                    connectedUser.addLose(userAttempts);
                                    WordleServer.updateUser(connectedUser);
                                } else {
                                    String clue = provideClue(guess);
                                    out.println("CLUE: " + clue + ", hai a disposizione " + (MAX_ATTEMPTS-userAttempts) + " tentativi.");
                                }
                            }
                        }
                        break;
                }
            }
            System.out.println("[Client #" + client_id + "] disconnesso dal server.");
            socket.close();
            in.close();
            out.close();
        } catch (Exception e) {
            System.err.println("Errore nella comunicazione con il client.");
            e.printStackTrace();
        }
    }

    public String provideClue(String guessWord) {
        /* Legenda:
         * GRIGIO: X, VERDE: +, GIALLO: ?
         * GRIGIO: lettera non appartenente alla parola segreta
         * VERDE: lettera appartenente alla parola segreta e in posizione corretta
         * GRIGIO: lettera corretta ma in posizione sbagliata
         */

        char[] guessWord_CA = guessWord.toCharArray(); // converto guessWord in array di char per semplificarne la manipolazione
        char[] secretWord_CA = secretWord.toCharArray();
        char[] clue_CA = new char[10];

        for (int i = 0; i < guessWord.length(); i++) {
            if (guessWord_CA[i] == secretWord_CA[i]) {
                clue_CA[i] = '+';
            } else if (secretWord.indexOf(guessWord_CA[i]) != -1) {
                // lettera presente nella parola, ma  in posizione sbagliata
                clue_CA[i] = '?';
            } else {
                clue_CA[i] = 'X';
            }
        }

        return Arrays.toString(clue_CA);
    }
}
