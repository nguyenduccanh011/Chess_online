package common;

public class JoinRoomMessage implements Message{
    public String type = "join_room";
    public String roomId;
    public JoinRoomMessage(String roomId){
        this.roomId = roomId;
    }
}
