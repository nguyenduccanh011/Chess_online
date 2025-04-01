package common;
import java.util.List;

public class LegalMovesMessage implements Message{
    public String type = "legal_moves";
    public List<String> moves;
    public LegalMovesMessage(List<String> moves){
        this.moves = moves;
    }
}