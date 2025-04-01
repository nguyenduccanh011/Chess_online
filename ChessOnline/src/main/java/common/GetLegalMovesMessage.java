package common;

import java.util.List;

public class GetLegalMovesMessage implements Message{
    public String type = "get_legal_moves";
    public String from;
    public GetLegalMovesMessage(String from){
        this.from = from;
    }
}