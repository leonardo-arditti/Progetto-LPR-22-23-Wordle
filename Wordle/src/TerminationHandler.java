
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Leonardo Arditti 25/4/2023
 */
/**
 * Classe che implementa l'handler di terminazione per il server. Questo thread
 * viene avviato al momento della pressione dei tasti CTRL+C. Lo scopo e' quello
 * di far terminare il main del server bloccato sulla accept() in attesa di
 * nuove connessioni e chiudere il pool di thread.
 */
public class TerminationHandler extends Thread {

    private int maxDelay;
    private ExecutorService pool;
    private ServerSocket serverSocket;

    public TerminationHandler(int maxDelay, ExecutorService pool, ServerSocket serverSocket) {
        this.maxDelay = maxDelay;
        this.pool = pool;
        this.serverSocket = serverSocket;
    }

    public void run() {
        // Avvio la procedura di terminazione del server.
        System.out.println("[SERVER] Avvio terminazione...");
        // Chiudo la ServerSocket in modo tale da non accettare piu' nuove richieste.
        try {
            serverSocket.close();
        } catch (IOException e) {
            System.err.printf("[SERVER] Errore: %s\n", e.getMessage());
        }
        // Faccio terminare il pool di thread.
        pool.shutdown();
        try {
            if (!pool.awaitTermination(maxDelay, TimeUnit.MILLISECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
        }
        System.out.println("[SERVER] Terminato.");
    }

}
