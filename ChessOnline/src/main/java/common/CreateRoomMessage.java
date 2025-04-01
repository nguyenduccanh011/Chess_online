package common;

public class CreateRoomMessage implements Message {
    public String type = "create_room";
    public String username; // Thêm trường username
}