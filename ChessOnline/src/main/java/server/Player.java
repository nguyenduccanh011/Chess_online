package server;
import java.net.Socket;

public class Player {
    private String username;
    private Socket clientSocket;

    public Player(String username, Socket clientSocket) {
        this.username = username;
        this.clientSocket = clientSocket;
    }

    public String getUsername() {
        return username;
    }

    public Socket getClientSocket() {
        return clientSocket;
    }
}