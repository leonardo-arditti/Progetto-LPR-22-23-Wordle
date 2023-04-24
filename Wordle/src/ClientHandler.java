
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
                String cmd = in.next(); // comando nella sua forma completa, e.g: REGISTER,username,password

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
                        }
                        break;

                    case "LOGOUT":
                        username = cmd_components[1];
                        if (!connectedUser.getUsername().equals(username)) {
                            out.println("ERROR");
                        } else {
                            connectedUser.setNotLoggedIn();
                            WordleServer.updateUser(username, connectedUser);
                            out.println("SUCCESS");
                            logged_out = true;
                            break;
                        }
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
}
