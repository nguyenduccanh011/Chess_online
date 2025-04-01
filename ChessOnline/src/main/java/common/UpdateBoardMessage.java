package common;

public class UpdateBoardMessage implements Message{
    public String type = "update_board";
    public String fen;
    public UpdateBoardMessage(String fen){
        this.fen = fen;
    }
}