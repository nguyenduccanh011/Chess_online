package common;

public class GameStartedMessage implements Message{
    public String type = "game_started";
    public String fen;
    public String turn;
    public GameStartedMessage(String fen, String turn){
        this.fen = fen;
        this.turn = turn;
    }
}