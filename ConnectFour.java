import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class ConnectFour implements Runnable {

    private String ip = "localhost";
    private int port = 22222;
    private Scanner scanner = new Scanner(System.in);
    private JFrame frame;
    private final int WIDTH = 709;
    private final int HEIGHT = 634;
    private Thread thread;

    private Painter painter;
    private Socket socket;
    private DataOutputStream dos;
    private DataInputStream dis;
    private ServerSocket serverSocket;

    private BufferedImage board;
    private BufferedImage redPiece;
    private BufferedImage yellowPiece;

    private int[][] boardState = new int[7][6]; // 7 columns and 6 rows for Connect 4
    private int currentPlayer = 1; // Player 1 is red, Player 2 is yellow

    private boolean yourTurn = false;
    private boolean accepted = false;
    private boolean red = true;
    private boolean unableToCommunicateWithOpponent = false;
    private boolean won = false;
    private boolean enemyWon = false;
    private boolean tie = false;

    private int selectedColumn = -1;
    private int selectedRow = -1;

    private Font font = new Font("Verdana", Font.BOLD, 32);
    private Font largerFont = new Font("Verdana", Font.BOLD, 50);

    private String waitingString = "Waiting for another player";
    private String unableToCommunicateWithOpponentString = "Unable to communicate with opponent.";
    private String wonString = "You won!";
    private String enemyWonString = "Opponent won!";
    private String tieString = "Game ended in a tie.";

    public ConnectFour() {
        System.out.println("Please input the IP: ");
		ip = scanner.nextLine();
		System.out.println("Please input the port: ");
		port = scanner.nextInt();
		while (port < 1 || port > 65535) {
			System.out.println("The port you entered was invalid, please input another port: ");
			port = scanner.nextInt();
		}

        loadImages();

        painter = new Painter();
        painter.setPreferredSize(new Dimension(WIDTH, HEIGHT));

        if (!connect()) {
            initializeServer();
            System.out.println("Initialize server");
        }

        frame = new JFrame();
        frame.setTitle("Connect 4");
        frame.setContentPane(painter);
        frame.setSize(WIDTH, HEIGHT);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setVisible(true);

        thread = new Thread(this, "Connect4Game");
        thread.start();
    }

    public void run() {
        System.out.println("run method");
        while (true) {
            tick();
            painter.repaint();

            if (!red && !accepted) {
                listenForServerRequest();
                System.out.println("Listening for server request.");
            }
        }
    }

    private void render(Graphics g) {
        g.drawImage(board, 0, 0, null);
        // Render the game pieces
        for (int col = 0; col < 7; col++) {
            for (int row = 0; row < 6; row++) {
                if (boardState[col][row] == 1) {
                    g.drawImage(redPiece, col * 100, row * 100, null);
                } else if (boardState[col][row] == 2) {
                    g.drawImage(yellowPiece, col * 100, row * 100, null);
                }
            }
        }
        // Render game outcome messages
        if (unableToCommunicateWithOpponent) {
            g.setColor(Color.BLACK);
            g.setFont(font);
            g.drawString(unableToCommunicateWithOpponentString, WIDTH / 2 - 200, HEIGHT / 2);
        } else if (accepted) {
            if (won || enemyWon) {
                g.setColor(Color.BLACK);
                g.setFont(largerFont);
                if (won) {
                    g.drawString(wonString, WIDTH / 2 - 100, HEIGHT / 2);
                } else {
                    g.drawString(enemyWonString, WIDTH / 2 - 120, HEIGHT / 2);
                }
            } else if (tie) {
                g.setColor(Color.BLACK);
                g.setFont(largerFont);
                g.drawString(tieString, WIDTH / 2 - 175, HEIGHT / 2);
            }
        } else {
            g.setColor(Color.BLACK);
            g.setFont(font);
            g.drawString(waitingString, WIDTH / 2 - 200, HEIGHT / 2);
        }
    }

    private void tick() {
        if (!yourTurn && !unableToCommunicateWithOpponent) {
            try {
                int col = dis.readInt();
                int row = dis.readInt();
                boardState[col][row] = 2; // Opponent's move
                checkForEnemyWin();
                checkForTie();
                yourTurn = true;
                System.out.println("Data was received");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void checkForWin() {
        // Check for a win in horizontal, vertical, and diagonal directions
        for (int player = 1; player <= 2; player++) {
            for (int col = 0; col < 7; col++) {
                for (int row = 0; row < 3; row++) {
                    if (boardState[col][row] == player && boardState[col][row + 1] == player && boardState[col][row + 2] == player && boardState[col][row + 3] == player) {
                        won = true;
                        return;
                    }
                }
            }
    
            for (int col = 0; col < 4; col++) {
                for (int row = 0; row < 6; row++) {
                    if (boardState[col][row] == player && boardState[col + 1][row] == player && boardState[col + 2][row] == player && boardState[col + 3][row] == player) {
                        won = true;
                        return;
                    }
                }
            }
    
            for (int col = 0; col < 4; col++) {
                for (int row = 0; row < 3; row++) {
                    if (boardState[col][row] == player && boardState[col + 1][row + 1] == player && boardState[col + 2][row + 2] == player && boardState[col + 3][row + 3] == player) {
                        won = true;
                        return;
                    }
                }
            }
    
            for (int col = 0; col < 4; col++) {
                for (int row = 3; row < 6; row++) {
                    if (boardState[col][row] == player && boardState[col + 1][row - 1] == player && boardState[col + 2][row - 2] == player && boardState[col + 3][row - 3] == player) {
                        won = true;
                        return;
                    }
                }
            }
        }
    }
    
    private void checkForEnemyWin() {
        // Similar to checkForWin, but for the opponent (current player's opponent)
        int opponentPlayer = (currentPlayer == 1) ? 2 : 1;
    
        for (int col = 0; col < 7; col++) {
            for (int row = 0; row < 3; row++) {
                if (boardState[col][row] == opponentPlayer && boardState[col][row + 1] == opponentPlayer && boardState[col][row + 2] == opponentPlayer && boardState[col][row + 3] == opponentPlayer) {
                    enemyWon = true;
                    return;
                }
            }
        }
    
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 6; row++) {
                if (boardState[col][row] == opponentPlayer && boardState[col + 1][row] == opponentPlayer && boardState[col + 2][row] == opponentPlayer && boardState[col + 3][row] == opponentPlayer) {
                    enemyWon = true;
                    return;
                }
            }
        }
    
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 3; row++) {
                if (boardState[col][row] == opponentPlayer && boardState[col + 1][row + 1] == opponentPlayer && boardState[col + 2][row + 2] == opponentPlayer && boardState[col + 3][row + 3] == opponentPlayer) {
                    enemyWon = true;
                    return;
                }
            }
        }
    
        for (int col = 0; col < 4; col++) {
            for (int row = 3; row < 6; row++) {
                if (boardState[col][row] == opponentPlayer && boardState[col + 1][row - 1] == opponentPlayer && boardState[col + 2][row - 2] == opponentPlayer && boardState[col + 3][row - 3] == opponentPlayer) {
                    enemyWon = true;
                    return;
                }
            }
        }
    }
    
    private void checkForTie() {
        // Check for a tie game
        for (int col = 0; col < 7; col++) {
            for (int row = 0; row < 6; row++) {
                if (boardState[col][row] == 0) {
                    return; // At least one empty cell is found; the game is not a tie
                }
            }
        }
        tie = true; // All cells are filled; the game is a tie
    }
    

    private void listenForServerRequest() {
        try {
            socket = serverSocket.accept();
            dos = new DataOutputStream(socket.getOutputStream());
            dis = new DataInputStream(socket.getInputStream());
            accepted = true;
            System.out.println("CLIENT HAS REQUESTED TO JOIN, AND WE HAVE ACCEPTED");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Client is not accepted");
        }
    }

    private boolean connect() {
        try {
            socket = new Socket(ip, port);
            dos = new DataOutputStream(socket.getOutputStream());
            dis = new DataInputStream(socket.getInputStream());
            accepted = true;
        } catch (IOException e) {
            System.out.println("Unable to connect to the address: " + ip + ":" + port + " | Starting a server");
            return false;
        }
        System.out.println("Successfully connected to the server.");
        return true;
    }

    private void initializeServer() {
        try {
            serverSocket = new ServerSocket(port, 8, InetAddress.getByName(ip));
            System.out.println("Server started at " + ip + ":" + port);
        } catch (Exception e) {
            e.printStackTrace();
        }
        yourTurn = true;
        red = false;
    }

    private void loadImages() {
    try {
        // Load the Connect 4 game board image
        board = ImageIO.read(getClass().getResource("res/board2.png"));

        // Load the red game piece image
        redPiece = ImageIO.read(getClass().getResource("res/redPiece.png"));

        // Load the yellow game piece image
        yellowPiece = ImageIO.read(getClass().getResource("res/yellowPiece.png"));
    } catch (IOException e) {
        System.err.println("Error loading image files: " + e.getMessage());
        // Handle the error appropriately (e.g., logging, displaying an error message)
    }
}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ConnectFour();
        });
    }

    private class Painter extends JPanel implements MouseListener {
        private static final long serialVersionUID = 1L;

        public Painter() {
            setFocusable(true);
            requestFocus();
            setPreferredSize(new Dimension(WIDTH, HEIGHT));
            setBackground(Color.WHITE);
            addMouseListener(this);
        }

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            render(g);
        }

        @Override
public void mouseClicked(MouseEvent e) {
    if (accepted) {
        if (yourTurn && !unableToCommunicateWithOpponent && !won && !enemyWon && !tie) {
            int x = e.getX() / 100; // Calculate the selected column
            if (x >= 0 && x < 7 && boardState[x][0] == 0) {
                for (int row = 5; row >= 0; row--) {
                    if (boardState[x][row] == 0) {
                        boardState[x][row] = 1; // Your move
                        selectedColumn = x;
                        selectedRow = row;
                        // Update yourTurn here
                        yourTurn = false;
                        repaint();
                        Toolkit.getDefaultToolkit().sync();
                        try {
                            dos.writeInt(selectedColumn);
                            dos.writeInt(selectedRow);
                            dos.flush();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                        System.out.println("DATA WAS SENT");
                        checkForWin();
                        checkForTie();
                        break;
                    }
                }
            }
        }
    }
}


        @Override
        public void mousePressed(MouseEvent e) {
        }

        @Override
        public void mouseReleased(MouseEvent e) {
        }

        @Override
        public void mouseEntered(MouseEvent e) {
        }

        @Override
        public void mouseExited(MouseEvent e) {
        }
    }
}
