/** 
 * @author Leonardo Arditti 24/4/2023
 */
public class User {
    private String username;
    private String password;
    private int total_played_games;
    private int games_won;
    private int current_winstreak;
    private int longest_winstreak;
    private int[] guess_distribution;
    
    private boolean has_played;

    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.total_played_games = 0;
        this.games_won = 0;
        this.current_winstreak = 0;
        this.longest_winstreak = 0;
        this.guess_distribution = new int[12];
        this.has_played = false;
    }
}
