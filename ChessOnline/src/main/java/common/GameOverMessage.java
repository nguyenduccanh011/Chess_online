package common;

public class GameOverMessage implements Message {
    public String type = "game_over";
    public String result; // "1-0", "0-1", "1/2-1/2"
    public String reason; // "checkmate", "stalemate", "draw by agreement", ...

    public GameOverMessage(String result, String reason) {
        this.result = result;
        this.reason = reason;
    }
}