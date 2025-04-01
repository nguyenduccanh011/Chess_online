package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;

import client.gui.ChessBoardGUI;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import common.*;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChessClient {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private BufferedReader consoleIn;
    private static final String SERVER_ADDRESS = "192.168.120.98";
    private static final int SERVER_PORT = 5000;
    private Gson gson = new Gson();
    private ChessBoardGUI gui;
    private String playerUsername;
    private boolean isWhite;
    private boolean isMyTurn = false;
    boolean firstGameStartedMessageReceived = false;
    private volatile List<Move> legalMoves = new ArrayList<>();
    private CountDownLatch latch = new CountDownLatch(1);

    public ChessClient() {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            System.out.println("Connected to server at " + SERVER_ADDRESS + ":" + SERVER_PORT);

            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            consoleIn = new BufferedReader(new InputStreamReader(System.in));

            // Gửi tin nhắn connect ngay lập tức
            ConnectMessage connectMsg = new ConnectMessage();
            playerUsername = "Player-" + UUID.randomUUID().toString().substring(0, 8); // Tạo username với UUID
            connectMsg.username = playerUsername;
            out.println(gson.toJson(connectMsg));
            System.out.println("Sent connect message with username: " + playerUsername);

            // Lắng nghe tin nhắn từ server trong một luồng riêng
            new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = in.readLine()) != null) {
                        System.out.println("Server: " + serverMessage);
                        handleServerMessage(serverMessage); // Gọi hàm xử lý tin nhắn
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            // Xử lý input từ console trong luồng riêng
            new Thread(() -> {
                try {
                    String userInput;
                    while ((userInput = consoleIn.readLine()) != null) {
                        handleConsoleInput(userInput);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setGUI(ChessBoardGUI gui) {
        this.gui = gui;
    }

    // Các hàm xử lý message
    private void handleConsoleInput(String userInput) {
        if (userInput.startsWith("{")) {
            // Có thể là JSON, thử phân tích
            try {
                JsonObject jsonObject = gson.fromJson(userInput, JsonObject.class);
                String messageType = jsonObject.get("type").getAsString();
                switch (messageType) {
                    case "create_room":
                        // Lấy username từ người dùng
                        String username = "Player-" + UUID.randomUUID().toString().substring(0, 8);
                        CreateRoomMessage createRoomMsg = new CreateRoomMessage();
                        createRoomMsg.type = "create_room";
                        createRoomMsg.username = username;
                        out.println(gson.toJson(createRoomMsg));
                        break;
                    case "join_room":
                        JoinRoomMessage joinRoomMsg = gson.fromJson(userInput, JoinRoomMessage.class);
                        out.println(gson.toJson(joinRoomMsg));
                        break;
                    case "move":
                        MoveMessage moveMsg = gson.fromJson(userInput, MoveMessage.class);
                        out.println(gson.toJson(moveMsg));
                        break;
                    default:
                        ChatMessage chatMsg = new ChatMessage();
                        chatMsg.message = userInput;
                        out.println(gson.toJson(chatMsg));
                        break;
                }
            } catch (JsonSyntaxException e) {
                // Không phải JSON hợp lệ, gửi tin nhắn chat
                ChatMessage chatMsg = new ChatMessage();
                chatMsg.message = userInput;
                out.println(gson.toJson(chatMsg));
            }
        } else {
            // Không bắt đầu bằng {, gửi tin nhắn chat
            ChatMessage chatMsg = new ChatMessage();
            chatMsg.message = userInput;
            out.println(gson.toJson(chatMsg));
        }
    }

    public void sendMoveMessage(String from, String to) {
        MoveMessage moveMsg = new MoveMessage(from, to);
        out.println(gson.toJson(moveMsg));
        System.out.println("Sent move message: " + from + " to " + to);
    }

    public Socket getSocket() {
        return socket;
    }

    public List<Move> getLegalMoves(String from) {
        latch = new CountDownLatch(1);
        legalMoves.clear();
        // Gửi yêu cầu lấy danh sách nước đi hợp lệ tới server
        out.println(gson.toJson(new GetLegalMovesMessage(from)));
        // Chờ nhận danh sách nước đi hợp lệ từ server
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return new ArrayList<>(legalMoves); // Trả về bản sao của danh sách
    }

    public void createRoom() {
        out.println(gson.toJson(new CreateRoomMessage()));
    }

    public void joinRoom(String roomId) {
        out.println(gson.toJson(new JoinRoomMessage(roomId)));
    }

    private void handleServerMessage(String serverMessage) {
        // Xử lý các tin nhắn từ server
        try {
            JsonObject jsonObject = gson.fromJson(serverMessage, JsonObject.class);
            String messageType = jsonObject.get("type").getAsString();

            switch (messageType) {
                case "welcome":
                    WelcomeMessage welcomeMsg = gson.fromJson(serverMessage, WelcomeMessage.class);
                    System.out.println(welcomeMsg.message);
                    break;
                case "chat":
                    ChatFromServerMessage chatMsg = gson.fromJson(serverMessage, ChatFromServerMessage.class);
                    System.out.println(chatMsg.sender + ": " + chatMsg.message);
                    break;
                case "room_created":
                    RoomCreatedMessage roomCreatedMsg = gson.fromJson(serverMessage, RoomCreatedMessage.class);
                    if (gui != null) {
                        SwingUtilities.invokeLater(() -> gui.setRoomId(roomCreatedMsg.roomId));
                    }
                    break;
                case "room_joined":
                    RoomJoinedMessage roomJoinedMsg = gson.fromJson(serverMessage, RoomJoinedMessage.class);
                    if (gui != null) {
                        // Cập nhật isWhite cho gui
                        gui.setIsWhite(roomJoinedMsg.isWhite);
                        this.isWhite = roomJoinedMsg.isWhite; // Cập nhật isWhite cho client
                        SwingUtilities.invokeLater(() -> {
                            gui.setRoomId(roomJoinedMsg.roomId);
                            gui.setBoardOrientation(isWhite);
                            gui.setIsWhite(isWhite);
                            gui.setVisible(true);
                        });
                    }
                    break;
                case "game_started":
                    if(!firstGameStartedMessageReceived){
                        GameStartedMessage gameStartedMsg = gson.fromJson(serverMessage, GameStartedMessage.class);
                        System.out.println("Game started! FEN: " + gameStartedMsg.fen + ", Turn: " + gameStartedMsg.turn + ", isWhite: " + isWhite);
                        isMyTurn = (gameStartedMsg.turn.equals("white") && isWhite) || (gameStartedMsg.turn.equals("black") && !isWhite);
                        System.out.println("isMyTurn (GameStartedMessage): " + isMyTurn);
                        if (gui != null) {
                            SwingUtilities.invokeLater(() -> {
                                gui.setMyTurn(isMyTurn);
                                //gui.updateBoard(gameStartedMsg.fen);
                            });
                        }
                        firstGameStartedMessageReceived = true;
                    }else if(firstGameStartedMessageReceived){
                        break;
                    }
                    break;
                case "update_board":
                    UpdateBoardMessage updateBoardMsg = gson.fromJson(serverMessage, UpdateBoardMessage.class);
                    if (gui != null) {
                        SwingUtilities.invokeLater(() -> {
                            gui.updateBoard(updateBoardMsg.fen);

                            // Cập nhật isMyTurn
                            isMyTurn = determineTurnFromFEN(updateBoardMsg.fen, isWhite);
                            System.out.println("isMyTurn (UpdateBoardMessage): " + isMyTurn);
                            gui.setMyTurn(isMyTurn);
                        });
                    }
                    break;
                case "error":
                    final String finalServerMessage = serverMessage;
                    SwingUtilities.invokeLater(() -> {
                        if (gui != null) {
                            ErrorMessage errorMsg = gson.fromJson(finalServerMessage, ErrorMessage.class);
                            gui.handleErrorMessage(errorMsg.message);
                        }
                    });
                    break;
                case "legal_moves":
                    LegalMovesMessage movesMsg = gson.fromJson(serverMessage, LegalMovesMessage.class);
                    synchronized (this) {
                        legalMoves.clear(); // Xóa danh sách nước đi hợp lệ cũ
                        for (String moveStr : movesMsg.moves) {
                            legalMoves.add(new Move(moveStr, Side.WHITE)); // Giả sử client luôn là quân trắng
                        }
                        latch.countDown();
                    }
                    break;
                case "game_over":
                    GameOverMessage gameOverMsg = gson.fromJson(serverMessage, GameOverMessage.class);
                    if (gui != null) {
                        SwingUtilities.invokeLater(() -> {
                            String message = "";
                            if (gameOverMsg.result.equals("1-0")) {
                                message = "White wins by checkmate!";
                            } else if (gameOverMsg.result.equals("0-1")) {
                                message = "Black wins by checkmate!";
                            } else {
                                message = "Draw!";
                            }
                            if(gameOverMsg.reason.equals("checkmate")){
                                message += " by checkmate";
                            }else if(gameOverMsg.reason.equals("stalemate")){
                                message += " by stalemate";
                            }
                            JOptionPane.showMessageDialog(gui, message, "Game Over", JOptionPane.INFORMATION_MESSAGE);
                            gui.setEnableBoard(false); // Vô hiệu hóa bàn cờ
                        });
                    }
                    break;
                default:
                    System.out.println("Received unknown message type from server: " + messageType);
                    break;
            }
        } catch (JsonSyntaxException e) {
            System.err.println("Invalid JSON format from server: " + e.getMessage());
        }
    }

    private boolean determineTurnFromFEN(String fen, boolean isWhite) {
        String[] parts = fen.split(" ");
        if (parts.length > 1) {
            String turn = parts[1];
            // Sửa lại logic so sánh
            return (turn.equals("w") && isWhite) || (turn.equals("b") && !isWhite);
        }
        return false; // Hoặc xử lý lỗi FEN không hợp lệ
    }

    public static void main(String[] args) {
        ChessClient client = new ChessClient();
        SwingUtilities.invokeLater(() -> {
            ChessBoardGUI gui = new ChessBoardGUI(client);
            client.setGUI(gui);
            gui.setVisible(true);
        });
    }
}