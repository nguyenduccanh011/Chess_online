package common;

public class MoveMessage implements Message {
    public String type = "move";
    public String from;
    public String to;

    public MoveMessage(String from, String to){
        this.from = from;
        this.to = to;
    }
}