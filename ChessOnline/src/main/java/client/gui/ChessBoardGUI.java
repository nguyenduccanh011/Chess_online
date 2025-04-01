package client.gui;

import client.ChessClient;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import com.google.gson.Gson;
import common.MoveMessage;

import javax.swing.*;
import javax.swing.border.LineBorder;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ChessBoardGUI extends JFrame {

    private JPanel boardPanel;
    private JButton[][] squares;
    private int selectedRow = -1;
    private int selectedCol = -1;
    private ChessClient client;
    private Gson gson;
    private String from;
    private String to;
    private boolean isWhite;
    private String currentFEN;
    private boolean isMyTurn;
    private boolean initialized = false;
    private Move pendingMove = null;
    private Color darkGreen = new Color(5, 171, 27); // Màu xanh đậm
    private List<Move> legalMoves = new ArrayList<>();

    // Thêm dòng khai báo biến turnLabel ở đây
    private JLabel turnLabel;
    private JButton createRoomButton;
    private JButton joinRoomButton;
    private JTextField roomIdField;
    private JLabel roomIdLabel;

    public ChessBoardGUI(ChessClient client) {
        this.client = client;
        this.currentFEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

        this.gson = new Gson();

        // Tạo phòng
        createRoomButton = new JButton("Create Room");
        createRoomButton.addActionListener(e -> {
            client.createRoom();
        });

        // Tham gia phòng
        joinRoomButton = new JButton("Join Room");
        joinRoomButton.addActionListener(e -> {
            String roomId = roomIdField.getText();
            if (!roomId.isEmpty()) {
                client.joinRoom(roomId);
            } else {
                JOptionPane.showMessageDialog(this, "Please enter a Room ID.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        roomIdField = new JTextField(10);

        setTitle("Chess Online");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(500, 500);
        setLayout(new BorderLayout());

        boardPanel = new JPanel(new GridLayout(8, 8));
        squares = new JButton[8][8]; // Khởi tạo squares ở đây

        initializeBoard();

        add(boardPanel, BorderLayout.CENTER);
        // Khởi tạo và thêm turnLabel vào panel
        turnLabel = new JLabel("Turn: ");
        add(turnLabel, BorderLayout.SOUTH);


        // Label hiển thị ID phòng
        roomIdLabel = new JLabel("Room ID: ");

        // Thêm các components vào GUI
        JPanel topPanel = new JPanel();
        topPanel.add(createRoomButton);
        topPanel.add(new JLabel("Room ID:"));
        topPanel.add(roomIdField);
        topPanel.add(joinRoomButton);

        add(topPanel, BorderLayout.NORTH);
        // Thêm roomIdLabel vào SOUTH
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomPanel.add(roomIdLabel);
        bottomPanel.add(turnLabel);
        add(bottomPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // Hiển thị ID phòng
    public void setRoomId(String roomId) {
        SwingUtilities.invokeLater(() -> roomIdLabel.setText("Room ID: " + roomId));
    }

    private void initializeBoard() {
        // Chỉ chạy initializeBoard() một lần
        if (!initialized) {
            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    squares[row][col] = new JButton();
                    squares[row][col].setBackground(((row + col) % 2 == 0) ? Color.WHITE : Color.GRAY);
                    squares[row][col].setPreferredSize(new Dimension(60, 60));
                    // Thêm viền cho tất cả các ô
                    squares[row][col].setBorder(new LineBorder(Color.BLACK));
                    final int finalRow = row;
                    final int finalCol = col;
                    squares[row][col].addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            handleSquareClick(finalRow, finalCol);
                        }
                    });
                    boardPanel.add(squares[row][col]);
                }
            }
            initialized = true; // Đánh dấu đã initialize xong
        }
    }

    // Thêm hàm setMyTurn()
    public void setMyTurn(boolean isMyTurn) {
        this.isMyTurn = isMyTurn;
        SwingUtilities.invokeLater(() -> {
            if (isMyTurn) {
                turnLabel.setText("Turn: Your turn");
            } else {
                turnLabel.setText("Turn: Opponent's turn");
            }
        });
    }


    public void setEnableBoard(boolean enable){
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                squares[row][col].setEnabled(enable);
            }
        }
    }

    private void handleSquareClick(int row, int col) {
        if (!isMyTurn) {
            return;
        }
        if (selectedRow == -1) {
            // Kiểm tra ô được chọn có quân cờ không
            if (squares[row][col].getIcon() != null) {
                selectedRow = row;
                selectedCol = col;
                squares[row][col].setBackground(Color.YELLOW);

                // Lấy danh sách các nước đi hợp lệ từ server
                String from = toAlgebraicNotation(selectedRow, selectedCol);

                // Di chuyển logic lấy legalMoves vào đây:
                legalMoves = client.getLegalMoves(from);

                // Tô màu các ô đích của các nước đi hợp lệ
                for (Move move : legalMoves) {
                    int toRow = 7 - move.getTo().getRank().ordinal();
                    int toCol = move.getTo().getFile().ordinal();
                    squares[toRow][toCol].setBackground(darkGreen);
                    squares[toRow][toCol].setBorder(BorderFactory.createLineBorder(Color.BLACK)); // Thêm viền đen
                }
            }

        } else {
            // Đã chọn ô đích, xử lý di chuyển quân cờ
            // Kiểm tra nếu nước đi nằm trong danh sách nước đi hợp lệ
            Move intendedMove = new Move(Square.fromValue(toAlgebraicNotation(selectedRow, selectedCol).toUpperCase()), Square.fromValue(toAlgebraicNotation(row, col).toUpperCase()));
            if (legalMoves.contains(intendedMove)) {
                // Nước đi hợp lệ, gửi thông tin nước đi cho server
                pendingMove = intendedMove;
                client.sendMoveMessage(toAlgebraicNotation(selectedRow, selectedCol), toAlgebraicNotation(row, col));
                setEnableBoard(false); // Disable bàn cờ trong khi chờ phản hồi từ server
            } else {
                // Nước đi không hợp lệ, hiển thị thông báo lỗi
                JOptionPane.showMessageDialog(this, "Invalid move!", "Error", JOptionPane.ERROR_MESSAGE);
            }

            // Bỏ chọn quân cờ và reset màu nền của các ô
            resetSquareColors();
            selectedRow = -1;
            selectedCol = -1;
        }
    }

    private void resetSquareColors() {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                squares[row][col].setBackground(getSquareColor(row, col));
                squares[row][col].setBorder(new LineBorder(Color.BLACK)); // Đặt lại viền đen
            }
        }
    }

    // Hàm di chuyển quân cờ thực sự trên GUI (tách ra từ movePiece)
    private void movePieceOnGUI(int fromRow, int fromCol, int toRow, int toCol) {
        Icon pieceIcon = squares[fromRow][fromCol].getIcon();
        squares[fromRow][fromCol].setIcon(null);
        squares[toRow][toCol].setIcon(pieceIcon);
    }

    public void handleErrorMessage(String message) {
        // Hiển thị thông báo lỗi
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);

        // Reset pendingMove
        pendingMove = null;

        // Enable tương tác với bàn cờ
        setEnableBoard(true);
    }


    private Color getSquareColor(int row, int col) {
        return (row + col) % 2 == 0 ? Color.WHITE : Color.GRAY;
    }

    private ImageIcon loadPieceIcon(char pieceChar, char colorChar) {
        String color = (colorChar == 'w') ? "white" : "black";
        String piece;
        switch (pieceChar) {
            case 'K':
            case 'k':
                piece = "king";
                break;
            case 'Q':
            case 'q':
                piece = "queen";
                break;
            case 'R':
            case 'r':
                piece = "rook";
                break;
            case 'B':
            case 'b':
                piece = "bishop";
                break;
            case 'N':
            case 'n':
                piece = "knight";
                break;
            case 'P':
            case 'p':
                piece = "pawn";
                break;
            default:
                return null;
        }
        // Đường dẫn tương đối, bắt đầu từ thư mục resources
        String imagePath = "images/" + color + "_" + piece + ".png";

        // Load từ resources
        URL imageUrl = getClass().getClassLoader().getResource(imagePath);

        if (imageUrl != null) {
            ImageIcon imageIcon = new ImageIcon(imageUrl);
            return imageIcon;
        } else {
            System.err.println("Could not find image: " + imagePath + ". URL is null."); // Sửa thông báo lỗi
            return null;
        }
    }
    public synchronized void updateBoard(String fen) {
        // Xóa toàn bộ icon trên bàn cờ
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                squares[row][col].setIcon(null);
            }
        }

        int row = 0, col = 0;
        String[] parts = fen.split(" ");
        if (parts.length < 1) {
            System.err.println("Error: Invalid FEN - missing board part");
            return;
        }
        String boardPart = parts[0];
        for (int i = 0; i < boardPart.length(); i++) {
            char c = boardPart.charAt(i);

            if (c == '/') {
                row++;
                col = 0;
                if (row > 7) {
                    System.err.println("Error: Invalid FEN - too many rows");
                    return;
                }
            } else if (Character.isDigit(c)) {
                int emptySquares = Character.getNumericValue(c);
                for (int j = 0; j < emptySquares; j++) {
                    if (col > 7) {
                        System.err.println("Error: Invalid FEN - too many columns");
                        return;
                    }
                    squares[row][col].setIcon(null);
                    col++;
                }
            } else {
                if (col > 7) {
                    System.err.println("Error: Invalid FEN - too many columns");
                    return;
                }
                char colorChar = Character.isUpperCase(c) ? 'w' : 'b';
                ImageIcon icon = loadPieceIcon(Character.toLowerCase(c), colorChar);
                if (icon != null) {
                    squares[row][col].setIcon(icon);
                } else {
                    System.err.println("Error: Could not load icon for piece: " + c);
                }
                col++;
            }
        }
        if (row != 7) {
            System.err.println("Error: Invalid FEN - too few rows");
        }
        if (parts.length > 1) {
            String turn = parts[1].equals("w") ? "White" : "Black";
            turnLabel.setText("Turn: " + turn);
        }

        // Enable tương tác với bàn cờ
        setEnableBoard(true);
    }

    private String toAlgebraicNotation(int row, int col) {
        char file = (char) ('a' + col);
        char rank = (char) ('8' - row);
        return "" + file + rank;
    }

    public void setIsWhite(boolean isWhite) {
        if (this.isWhite != isWhite) {
            this.isWhite = isWhite;
            SwingUtilities.invokeLater(() -> {
                updateBoard(currentFEN);
                revalidate();
                repaint();
            });
        }
    }
    public boolean getIsWhite() {
        return isWhite;
    }

    public String getCurrentFEN() {
        return currentFEN;
    }

    public void setBoardOrientation(boolean isWhite) {
        this.isWhite = isWhite;
        if (boardPanel != null) {
            boardPanel.removeAll();
            if (isWhite) {
                for (int row = 0; row < 8; row++) {
                    for (int col = 0; col < 8; col++) {
                        boardPanel.add(squares[row][col]);
                    }
                }
            } else {
                for (int row = 7; row >= 0; row--) {
                    for (int col = 7; col >= 0; col--) {
                        boardPanel.add(squares[row][col]);
                    }
                }
            }
            boardPanel.revalidate();
            boardPanel.repaint();
        }
    }

}