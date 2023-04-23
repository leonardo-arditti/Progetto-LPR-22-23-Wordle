
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * @author Leonardo Arditti 23/4/2023
 */
public class WordleClient {
    // Percorso del file di configurazione del client
    public static final String CONFIG = "client.properties";
    
    // Nome host e porta del server.
    public static String HOSTNAME;
    public static int PORT;
    
    /**
     * Metodo che legge il file di configurazione del client
     *
     * @throws FileNotFoundException se il file non esiste
     * @throws IOException se si verifica un errore durante la lettura
     */
    public static void readConfig() throws FileNotFoundException, IOException {
        InputStream input = new FileInputStream(CONFIG);
        Properties prop = new Properties();
        prop.load(input);
        HOSTNAME = prop.getProperty("HOSTNAME");
        PORT = Integer.parseInt(prop.getProperty("PORT"));
        input.close();
    }

}
