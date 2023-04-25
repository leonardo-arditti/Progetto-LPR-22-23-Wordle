
import java.util.ArrayList;

/** 
 * @author Leonardo Arditti 24/4/2023
 */
public class User {
    private String username;
    private String password;
    private int total_played_games;
    private int total_games_won;
    private int current_winstreak;
    private int longest_winstreak;
    private ArrayList<Integer> guess_distribution;
    private boolean has_played; // possibile rimozione? (oppure uso una seconda mappa e la aggiorno quando cambia la parola?!)
    private boolean is_logged;
    
    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.total_played_games = 0;
        this.total_games_won = 0;
        this.current_winstreak = 0;
        this.longest_winstreak = 0;
        this.guess_distribution = new ArrayList<>();
        this.has_played = false;
    }

    public User(String username, String password, int total_played_games, int total_games_won, int current_winstreak, int longest_winstreak, boolean has_played, ArrayList<Integer> guess_distribution) {
        this.username = username;
        this.password = password;
        this.total_played_games = total_played_games;
        this.total_games_won = total_games_won;
        this.current_winstreak = current_winstreak;
        this.longest_winstreak = longest_winstreak;
        this.guess_distribution = guess_distribution;
        this.has_played = has_played;
    }
    
    public User() {
        this.is_logged = false;
    }
    
    public boolean isLoggedIn() {
        return this.is_logged;
    }
    
    public void setLoggedIn() {
        this.is_logged = true;
    }
    
    public void setNotLoggedIn() {
        this.is_logged = false;
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getPassword() {
        return this.password;
    }

    public int getTotal_played_games() {
        return total_played_games;
    }

    public int getTotal_games_won() {
        return total_games_won;
    }

    public int getCurrent_winstreak() {
        return current_winstreak;
    }

    public int getLongest_winstreak() {
        return longest_winstreak;
    }

    public ArrayList<Integer> getGuess_distribution() {
        return guess_distribution;
    }

    public boolean Has_played() {
        return has_played;
    }

    /* public boolean isIs_logged() {
        return is_logged;
    } */
    
    @Override
    public String toString() {
        return "User{" + "username=" + username + ", password=" + password + ", total_played_games=" + total_played_games + ", total_games_won=" + total_games_won + ", current_winstreak=" + current_winstreak + ", longest_winstreak=" + longest_winstreak + ", guess_distribution=" + guess_distribution + ", has_played=" + has_played + ", is_logged=" + is_logged + '}';
    }
}