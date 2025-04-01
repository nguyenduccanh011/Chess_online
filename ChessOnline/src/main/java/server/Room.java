package server;

import com.github.bhlangonijr.chesslib.Side;
import common.GameStartedMessage;
import common.RoomJoinedMessage;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.bhlangonijr.chesslib.Board;
import common.UpdateBoardMessage;

public class Room {
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(1);
    private String roomId;
    private ClientHandler player1;
    private ClientHandler player2;
    private com.github.bhlangonijr.chesslib.Board board; // Thay đổi kiểu dữ liệu thành com.github.bhlangonijr.chesslib.Board
    private Gson gson;
    private Side currentTurn;
    private CountDownLatch playersReadyLatch = new CountDownLatch(2);

    public Room(ClientHandler player1) {
        this.roomId = String.valueOf(ID_GENERATOR.getAndIncrement());
        this.player1 = player1;
        this.player2 = null;
        this.board = new com.github.bhlangonijr.chesslib.Board(); // Khởi tạo bàn cờ
        this.gson = new Gson();
        this.currentTurn = Side.WHITE; // Thêm dòng này, mặc định lượt đi đầu tiên là của quân trắng
    }

    public String getRoomId() {
        return roomId;
    }
    public void setPlayer1(ClientHandler player1) {
        this.player1 = player1;
    }

    public void setPlayer2(ClientHandler player2) {
        this.player2 = player2;
    }
    public synchronized boolean isFull() {
        return player2 != null;
    }

    public synchronized void join(ClientHandler player2) {
        this.player2 = player2;
        playersReadyLatch.countDown();
        if (playersReadyLatch.getCount() == 0) {
            notifyPlayersGameStarted();
        }
    }

    public ClientHandler getPlayer1() {
        return player1;
    }

    public ClientHandler getPlayer2() {
        return player2;
    }

    // Thông báo cho người chơi khi trò chơi bắt đầu
    public void notifyPlayersGameStarted() {
        try {
            playersReadyLatch.await(); // Chờ CountDownLatch về 0
            board.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
            currentTurn = Side.WHITE;

//            // Gửi GameStartedMessage cho player1 với turn là white
//            GameStartedMessage gameStartedMessage1 = new GameStartedMessage(board.getFen(), "white");
//            sendMessageToPlayer(player1, gson.toJson(gameStartedMessage1));

            // Gửi GameStartedMessage cho player1 với turn là white
            GameStartedMessage gameStartedMessage1 = new GameStartedMessage(board.getFen(), "white"); // Đảm bảo turn là "white"
            sendMessageToPlayer(player1, gson.toJson(gameStartedMessage1));

            // Gửi GameStartedMessage cho player2 với turn là black
            GameStartedMessage gameStartedMessage2 = new GameStartedMessage(board.getFen(), "black");
            sendMessageToPlayer(player2, gson.toJson(gameStartedMessage2)); // Thêm dòng này để gửi cho player2

            // Gửi UpdateBoardMessage
            UpdateBoardMessage updateBoardMsg = new UpdateBoardMessage(board.getFen());
            sendMessageToPlayer(player1, gson.toJson(updateBoardMsg));
            sendMessageToPlayer(player2, gson.toJson(updateBoardMsg));


        } catch (InterruptedException e) {
            //e.printStackTrace();
        }
    }

    // Thêm hàm getBoard()
    public Board getBoard() {
        return board;
    }

    // Sửa sendMessageToPlayer() thành public
    public void sendMessageToPlayer(ClientHandler player, String message) {
        try {
            PrintWriter out = new PrintWriter(player.getClientSocket().getOutputStream(), true);
            out.println(message);
        } catch (IOException e) {
            System.err.println("Error sending message to player: " + e.getMessage());
            e.printStackTrace();
        }
    }
    // Thêm hàm này để lấy currentTurn
    public Side getCurrentTurn() {
        return currentTurn;
    }
    //Thêm hàm này để cập nhật currentTurn
    public void switchTurn() {
        currentTurn = (currentTurn == Side.WHITE) ? Side.BLACK : Side.WHITE;
    }

    //Thêm hàm này vào Room.java
    public void notifyPlayerGameStarted(ClientHandler player) {
        GameStartedMessage gameStartedMessage = new GameStartedMessage(board.getFen(), currentTurn == Side.WHITE ? "white" : "black");
        sendMessageToPlayer(player, gson.toJson(gameStartedMessage));
    }

    public void countDownLatch() {
        playersReadyLatch.countDown();
    }

    public void sendMessageToAll(String message) {
        sendMessageToPlayer(player1, message);
        sendMessageToPlayer(player2, message);
    }

}