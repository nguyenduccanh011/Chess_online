

package common;

public class ChatFromServerMessage implements Message{
    public String type = "chat";
    public String sender;
    public String message;
}
