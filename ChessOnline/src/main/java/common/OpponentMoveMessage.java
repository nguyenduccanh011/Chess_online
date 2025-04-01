package common;


public class OpponentMoveMessage implements Message {
    public String type = "opponent_move";
    public String from;
    public String to;
}
