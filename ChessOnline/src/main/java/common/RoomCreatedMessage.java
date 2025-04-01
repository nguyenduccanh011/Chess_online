package common;
public class RoomCreatedMessage implements Message {
    public String type = "room_created";
    public String roomId;
}