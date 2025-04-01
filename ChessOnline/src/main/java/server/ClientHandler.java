package server;

import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import common.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private ChessServer server;
    private BufferedReader in;
    private PrintWriter out;
    private Gson gson;
    private Room currentRoom;
    private Player player;

    public ClientHandler(Socket clientSocket, ChessServer server) {
        this.clientSocket = clientSocket;
        this.server = server;
        this.gson = new Gson();
        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Socket getClientSocket() {
        return clientSocket;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    @Override
    public void run() {
        try {
            // Gửi thông điệp chào mừng
            WelcomeMessage welcomeMsg = new WelcomeMessage();
            welcomeMsg.message = "Welcome to the Chess Server!";
            out.println(gson.toJson(welcomeMsg));

            String clientMessage;
            while ((clientMessage = in.readLine()) != null) {
                // Nhận thông điệp JSON và phân tích loại thông điệp
                try {
                    JsonObject jsonObject = gson.fromJson(clientMessage, JsonObject.class);
                    String messageType = jsonObject.get("type").getAsString();

                    switch (messageType) {
                        case "connect":
                            ConnectMessage connectMsg = gson.fromJson(clientMessage, ConnectMessage.class);
                            this.player = new Player(connectMsg.username, clientSocket);
                            System.out.println("Received connect message from " + connectMsg.username);
                            break;
                        case "chat":
                            ChatMessage chatMsg = gson.fromJson(clientMessage, ChatMessage.class);
                            System.out.println("Received chat message from client: " + chatMsg.message);
                            // Gửi phản hồi (ví dụ)
                            ChatFromServerMessage chatFromServerMessage = new ChatFromServerMessage();
                            chatFromServerMessage.sender = "Server";
                            chatFromServerMessage.message = "You said: " + chatMsg.message;
                            out.println(gson.toJson(chatFromServerMessage));
                            break;
                        case "create_room":
                            if (currentRoom == null) {
                                currentRoom = server.createRoom(this);
                                System.out.println("Room created: " + currentRoom.getRoomId());
                                currentRoom.setPlayer1(this);

                                // Gửi phản hồi tạo phòng thành công
                                RoomCreatedMessage roomCreatedMsg = new RoomCreatedMessage();
                                roomCreatedMsg.roomId = currentRoom.getRoomId();
                                out.println(gson.toJson(roomCreatedMsg));
                            }else{
                                // Gửi ErrorMessage cho client
                                ErrorMessage errorMsg = new ErrorMessage();
                                errorMsg.message = "You are already in a room.";
                                out.println(gson.toJson(errorMsg));
                            }
                            break;
                        case "join_room":
                            JoinRoomMessage joinRoomMsg = gson.fromJson(clientMessage, JoinRoomMessage.class);
                            if (server.roomExists(joinRoomMsg.roomId)) {
                                Room joinedRoom = server.joinRoom(joinRoomMsg.roomId, this); // Nhận Room từ server.joinRoom()
                                if (joinedRoom != null) {
                                    if (player == null) {
                                        String username = "Player-" + UUID.randomUUID().toString().substring(0, 8);
                                        this.player = new Player(username, clientSocket);
                                    }

                                    // Gán currentRoom cho cả hai client
                                    this.currentRoom = joinedRoom;
                                    joinedRoom.setPlayer2(this); // Cập nhật player2 cho Room

                                    // Lấy ClientHandler của player1
                                    ClientHandler player1Handler = joinedRoom.getPlayer1();

                                    // Gán currentRoom cho player1
                                    if (player1Handler != null && player1Handler != this) {
                                        player1Handler.currentRoom = joinedRoom;
                                    }

                                    System.out.println("Player " + this.player.getUsername() + " joined room " + joinRoomMsg.roomId);
                                    joinedRoom.notifyPlayersGameStarted();

                                } else {
                                    // Xử lý trường hợp phòng không tồn tại hoặc đã đầy
                                    ErrorMessage joinRoomErrMsg = new ErrorMessage();
                                    joinRoomErrMsg.message = "Failed to join room " + joinRoomMsg.roomId + ". Room may not exist or is full.";
                                    out.println(gson.toJson(joinRoomErrMsg));
                                    System.out.println("Failed to join room request from " + this.player.getUsername() + ": Room not found or is full.");
                                }
                            } else {
                                // Xử lý trường hợp phòng không tồn tại
                                ErrorMessage joinRoomErrMsg = new ErrorMessage();
                                joinRoomErrMsg.message = "Room " + joinRoomMsg.roomId + " does not exist.";
                                out.println(gson.toJson(joinRoomErrMsg));
                                System.out.println("Failed to join room request from " + this.player.getUsername() + ": Room not found.");
                            }
                            break;
                        case "move":
                            MoveMessage moveMsg = gson.fromJson(clientMessage, MoveMessage.class);
                            System.out.println("Received move message from " + player.getUsername() + ": " + moveMsg.from + " to " + moveMsg.to);

                            if (currentRoom != null) {
                                // Kiểm tra xem người chơi hiện tại có phải là người đang đi không
                                Side sideToMove = currentRoom.getBoard().getSideToMove();
                                String currentPlayerUsername = player.getUsername();

                                if ((sideToMove == Side.WHITE && currentRoom.getPlayer1().getPlayer().getUsername().equals(currentPlayerUsername)) ||
                                        (sideToMove == Side.BLACK && currentRoom.getPlayer2().getPlayer().getUsername().equals(currentPlayerUsername))) {

                                    Move move = new Move(Square.fromValue(moveMsg.from.toUpperCase()), Square.fromValue(moveMsg.to.toUpperCase()));

                                    // Lấy quân cờ tại vị trí xuất phát
                                    com.github.bhlangonijr.chesslib.Piece pieceToMove = currentRoom.getBoard().getPiece(Square.fromValue(moveMsg.from.toUpperCase()));

                                    // Kiểm tra vị trí hiện tại của quân cờ và tính hợp lệ của nước đi (bao gồm cả chiếu tướng)
                                    List<Move> legalMoves = currentRoom.getBoard().legalMoves();
                                    if (legalMoves.contains(move)) {
                                        currentRoom.getBoard().doMove(move); // Thực hiện nước đi trên bàn cờ

                                        // Cập nhật lượt chơi
                                        currentRoom.switchTurn();

                                        // Gửi UpdateBoardMessage cho cả hai client
                                        UpdateBoardMessage updateBoardMsg = new UpdateBoardMessage(currentRoom.getBoard().getFen());
                                        String updateBoardJson = gson.toJson(updateBoardMsg);
                                        System.out.println("Sending FEN to clients: " + currentRoom.getBoard().getFen());

                                        // Gửi cho tất cả người chơi trong phòng (cần implement hàm sendMessageToAll trong Room)
                                        currentRoom.sendMessageToAll(updateBoardJson);

                                        // Kiểm tra kết thúc game
                                        if (currentRoom.getBoard().isMated()) {
                                            String result = currentRoom.getBoard().getSideToMove() == Side.WHITE ? "0-1" : "1-0"; // Bên vừa đi mà bị chiếu hết là thua
                                            GameOverMessage gameOverMsg = new GameOverMessage(result, "checkmate");
                                            String gameOverJson = gson.toJson(gameOverMsg);
                                            currentRoom.sendMessageToAll(gameOverJson); // Gửi cho tất cả người chơi
                                            // Có thể thêm logic để xóa phòng hoặc cho phép chơi lại
                                        } else if (currentRoom.getBoard().isDraw()) {
                                            GameOverMessage gameOverMsg = new GameOverMessage("1/2-1/2", "draw");
                                            String gameOverJson = gson.toJson(gameOverMsg);
                                            currentRoom.sendMessageToAll(gameOverJson); // Gửi cho tất cả người chơi
                                        } else if (currentRoom.getBoard().isStaleMate()) {
                                            GameOverMessage gameOverMsg = new GameOverMessage("1/2-1/2", "stalemate");
                                            String gameOverJson = gson.toJson(gameOverMsg);
                                            currentRoom.sendMessageToAll(gameOverJson); // Gửi cho tất cả người chơi
                                        }
                                    } else {
                                        // Nước đi không hợp lệ
                                        ErrorMessage errorMsg = new ErrorMessage();
                                        errorMsg.message = "Invalid move!";
                                        out.println(gson.toJson(errorMsg));
                                        System.out.println("Invalid move from " + player.getUsername() + ": " + moveMsg.from + " to " + moveMsg.to);
                                    }
                                } else {
                                    // Không phải lượt của người chơi
                                    ErrorMessage errorMsg = new ErrorMessage();
                                    errorMsg.message = "It's not your turn!";
                                    out.println(gson.toJson(errorMsg));
                                    System.out.println("Not " + player.getUsername() + "'s turn to move.");
                                }
                            }
                            break;
                        case "get_legal_moves":
                            GetLegalMovesMessage getLegalMovesMsg = gson.fromJson(clientMessage, GetLegalMovesMessage.class);
                            if (currentRoom != null) {
                                Square fromSquare = Square.fromValue(getLegalMovesMsg.from.toUpperCase());
                                List<Move> allMoves = currentRoom.getBoard().legalMoves();
                                List<String> filteredMoves = new ArrayList<>();

                                for (Move move : allMoves) {
                                    if (move.getFrom().equals(fromSquare)) { // Chỉ lấy các nước đi bắt đầu từ ô xuất phát
                                        filteredMoves.add(move.toString());
                                    }
                                }

                                LegalMovesMessage legalMovesMsg = new LegalMovesMessage(filteredMoves);
                                out.println(gson.toJson(legalMovesMsg));
                            } else {
                                // Gửi ErrorMessage nếu client chưa tham gia phòng
                                ErrorMessage errorMsg = new ErrorMessage();
                                errorMsg.message = "You are not in a room!";
                                out.println(gson.toJson(errorMsg));
                                System.err.println("Client " + player.getUsername() + " requested legal moves but is not in a room.");
                            }
                            break;
                        default:
                            System.out.println("Received unknown message type: " + messageType);
                            break;
                    }
                } catch (JsonSyntaxException e) {
                    System.err.println("Invalid JSON format: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Error in client handler: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}