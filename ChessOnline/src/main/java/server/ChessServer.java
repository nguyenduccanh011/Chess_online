package server;

import com.github.bhlangonijr.chesslib.Side;
import common.CreateRoomMessage;
import common.GameStartedMessage;
import common.RoomCreatedMessage;
import com.google.gson.Gson;
import common.RoomJoinedMessage;
import server.Room;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import server.ClientHandler;

public class ChessServer {
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private List<Room> rooms;
    private Gson gson;
    private static final int PORT = 5000;
    private RoomCreatedMessage roomCreatedMsg;

    public ChessServer() {
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Server is listening on port " + PORT);
            threadPool = Executors.newFixedThreadPool(10);
            rooms = new ArrayList<>();
            gson = new Gson();
        } catch (IOException e) {
            System.err.println("Could not listen on port " + PORT);
            e.printStackTrace();
        }
    }

    public void start() {
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress().getHostAddress());
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                threadPool.execute(clientHandler);
            } catch (IOException e) {
                System.err.println("Accept failed.");
                e.printStackTrace();
            }
        }
    }

    public synchronized Room createRoom(ClientHandler client) {
        Room newRoom = new Room(client); // Tạo Room mới
        rooms.add(newRoom);
        roomCreatedMsg = new RoomCreatedMessage();
        roomCreatedMsg.roomId = newRoom.getRoomId();

        // Gửi tin nhắn room_created cho người tạo phòng
        String roomCreatedJson = gson.toJson(roomCreatedMsg);
        System.out.println("Room created with ID: " + roomCreatedMsg.roomId + " by player: " + client.getPlayer().getUsername());

        // KHÔNG GỬI GameStartedMessage và UpdateBoardMessage ở đây

        newRoom.countDownLatch();
        return newRoom; // Trả về Room mới
    }


    // Trong lớp ChessServer.java
    public synchronized Room joinRoom(String roomId, ClientHandler client) {
        for (Room room : rooms) {
            if (room.getRoomId().equals(roomId) && !room.isFull()) {
                room.join(client);

                // Gửi tin nhắn room_joined cho người chơi 1
                RoomJoinedMessage roomJoinedMessage1 = new RoomJoinedMessage();
                roomJoinedMessage1.type = "room_joined";
                roomJoinedMessage1.roomId = room.getRoomId();
                roomJoinedMessage1.opponent = client.getPlayer().getUsername();
                System.out.println("Sending room_joined to player 1 (" + room.getPlayer1().getPlayer().getUsername() + "), isWhite: true");
                roomJoinedMessage1.isWhite = true;
                room.sendMessageToPlayer(room.getPlayer1(), gson.toJson(roomJoinedMessage1));

                // Gửi tin nhắn room_joined cho người chơi 2
                RoomJoinedMessage roomJoinedMessage2 = new RoomJoinedMessage();
                roomJoinedMessage2.type = "room_joined";
                roomJoinedMessage2.roomId = room.getRoomId();
                roomJoinedMessage2.opponent = room.getPlayer1().getPlayer().getUsername();
                System.out.println("Sending room_joined to player 2 (" + client.getPlayer().getUsername() + "), isWhite: false");
                roomJoinedMessage2.isWhite = false;
                room.sendMessageToPlayer(client, gson.toJson(roomJoinedMessage2));

                // Gửi GameStartedMessage và UpdateBoardMessage cho client 2
                //room.notifyPlayerGameStarted(client);
                return room;
            }
        }
        return null;
    }

    public synchronized void removeRoom(Room room) {
        rooms.remove(room);
    }

    public static void main(String[] args) {
        ChessServer server = new ChessServer();
        server.start();
    }
    public synchronized boolean roomExists(String roomId) {
        for (Room room : rooms) {
            if (room.getRoomId().equals(roomId)) {
                return true;
            }
        }
        return false;
    }

}