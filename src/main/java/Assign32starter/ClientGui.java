package Assign32starter;

import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.Socket;

public class ClientGui implements OutputPanel.EventHandlers {
    private JFrame frame;
    private CardLayout cardLayout;
    private JPanel cards;

    private JPanel mainMenuPanel;
    private JPanel leaderboardPanel;
    private JPanel gamePanel;

    private OutputPanel outputPanel;
    private JTextArea leaderboardText;
    private PicturePanel picturePanel;

    private Socket socket;
    private ObjectOutputStream os;
    private DataInputStream in;

    private String host;
    private int port;

    public ClientGui(String host, int port) {
        this.host = host;
        this.port = port;

        frame = new JFrame("Movie Guess Game");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(800, 600));

        cardLayout = new CardLayout();
        cards = new JPanel(cardLayout);

        initMainMenu();
        initLeaderboardPanel();
        initGamePanel();

        frame.add(cards);
        frame.pack();
        frame.setVisible(true);
    }

    private void initMainMenu() {
        mainMenuPanel = new JPanel();
        mainMenuPanel.setLayout(new GridLayout(3, 1, 10, 10));

        JButton startBtn = new JButton("Start");
        JButton leaderboardBtn = new JButton("Leader Board");
        JButton quitBtn = new JButton("Quit");

        startBtn.addActionListener(e -> showStartDialog());
        leaderboardBtn.addActionListener(e -> showLeaderboard());
        quitBtn.addActionListener(e -> quit());

        mainMenuPanel.add(startBtn);
        mainMenuPanel.add(leaderboardBtn);
        mainMenuPanel.add(quitBtn);

        cards.add(mainMenuPanel, "mainMenu");
    }

    private void initLeaderboardPanel() {
        leaderboardPanel = new JPanel(new BorderLayout());

        leaderboardText = new JTextArea();
        leaderboardText.setEditable(false);
        JScrollPane scroll = new JScrollPane(leaderboardText);
        leaderboardPanel.add(scroll, BorderLayout.CENTER);

        JButton backBtn = new JButton("Back");
        backBtn.addActionListener(e -> cardLayout.show(cards, "mainMenu"));
        leaderboardPanel.add(backBtn, BorderLayout.SOUTH);

        cards.add(leaderboardPanel, "leaderboard");
    }

    private void initGamePanel() {
        gamePanel = new JPanel(new BorderLayout());

        outputPanel = new OutputPanel();
        outputPanel.addEventHandlers(this);

        picturePanel = new PicturePanel();

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(picturePanel, BorderLayout.CENTER);

        gamePanel.add(centerPanel, BorderLayout.CENTER);
        gamePanel.add(outputPanel, BorderLayout.SOUTH);

        cards.add(gamePanel, "game");
    }

    private void showStartDialog() {
        JTextField nameField = new JTextField();
        JTextField durationField = new JTextField();
        Object[] fields = {
                "Name:", nameField,
                "Duration:", durationField
        };

        int result = JOptionPane.showConfirmDialog(frame, fields, "Start Game", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            String duration = durationField.getText().trim();
            if (!name.isEmpty() && !duration.isEmpty()) {
                sendStartRequest(name, duration);
                cardLayout.show(cards, "game");
                outputPanel.appendOutput("Game started for " + name);
            } else {
                JOptionPane.showMessageDialog(frame, "Name and duration are required!");
            }
        }
    }

    private void sendStartRequest(String name, String duration) {
        try {
            JSONObject request = new JSONObject();
            request.put("action", "start");
            request.put("name", name);
            request.put("duration", duration);

            connectIfNeeded();
            os.writeObject(request.toString());
            os.flush();

            String response = in.readUTF();
            JSONObject json = new JSONObject(response);
            outputPanel.appendOutput(response);

            String image = json.optString("image");
            if (!image.isEmpty()) {
                picturePanel.newGame(1); // Assuming a 1x1 grid to start with
                picturePanel.insertImage("img/" + image, 0, 0);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showLeaderboard() {
        try {
            JSONObject request = new JSONObject();
            request.put("action", "leaderboard");

            connectIfNeeded();
            os.writeObject(request.toString());
            os.flush();

            String response = in.readUTF();
            leaderboardText.setText(response);
            cardLayout.show(cards, "leaderboard");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void quit() {
        try {
            JSONObject request = new JSONObject();
            request.put("action", "quit");
            connectIfNeeded();
            os.writeObject(request.toString());
            os.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            disconnect();
            System.exit(0);
        }
    }

    private void connectIfNeeded() throws IOException {
        if (socket == null || socket.isClosed()) {
            socket = new Socket(host, port);
            os = new ObjectOutputStream(socket.getOutputStream());
            in = new DataInputStream(socket.getInputStream());
        }
    }

    private void disconnect() {
        try {
            if (os != null) os.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void submitClicked() {
        String inputText = outputPanel.getInputText().trim();
        if (inputText.isEmpty()) return;

        try {
            JSONObject request = new JSONObject();
            if (inputText.equals("next") || inputText.equals("skip") ||
                    inputText.equals("remaining") || inputText.equals("quit")) {
                request.put("action", inputText);
            } else {
                request.put("action", "guess");
                request.put("answer", inputText);
            }

            os.writeObject(request.toString());
            os.flush();

            String response = in.readUTF();
            JSONObject json = new JSONObject(response);

            outputPanel.appendOutput(response);

            String image = json.optString("image");
            if (!image.isEmpty()) {
                picturePanel.insertImage("img/" + image, 0, 0);
            }

            int points = json.optInt("points", -1);
            if (points >= 0) {
                outputPanel.setPoints(points);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void inputUpdated(String input) {
        if ("surprise".equalsIgnoreCase(input)) {
            outputPanel.appendOutput("🎉 Surprise triggered!");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new ClientGui("localhost", 8888);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}