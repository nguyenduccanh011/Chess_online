package common;

public class RoomJoinedMessage implements Message {
    public String type = "room_joined";
    public String roomId;
    public String opponent; // Thêm trường này
    public boolean isWhite;
}