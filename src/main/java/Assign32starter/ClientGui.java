package Assign32starter;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.*;
import java.net.Socket;
import javax.swing.JDialog;
import javax.swing.WindowConstants;
import org.json.JSONObject;

public class ClientGui implements OutputPanel.EventHandlers {
    JDialog frame;
    PicturePanel picPanel;
    OutputPanel outputPanel;

    Socket sock;
    ObjectOutputStream os;
    DataInputStream in;

    String host = "localhost";
    int port = 8888;

    volatile boolean running = true;

    public ClientGui(String host, int port) throws IOException {
        this.host = host;
        this.port = port;

        frame = new JDialog();
        frame.setLayout(new GridBagLayout());
        frame.setMinimumSize(new Dimension(500, 500));
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        picPanel = new PicturePanel();
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weighty = 0.25;
        frame.add(picPanel, c);

        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 1;
        c.weighty = 0.75;
        c.weightx = 1;
        c.fill = GridBagConstraints.BOTH;
        outputPanel = new OutputPanel();
        outputPanel.addEventHandlers(this);
        frame.add(outputPanel, c);

        picPanel.newGame(1);
        insertImage("img/TheDarkKnight1.png", 0, 0);

        open(); // socket connection ONCE

        // Start background thread to listen for server messages
        new Thread(this::listenToServer).start();

        // Optional: send a hello message to initialize
        JSONObject hello = new JSONObject();
        hello.put("type", "start");
        hello.put("action", "hello");
        sendToServer(hello);
    }

    public void show(boolean makeModal) {
        frame.pack();
        frame.setModal(makeModal);
        frame.setVisible(true);
    }

    public void newGame(int dimension) {
        picPanel.newGame(dimension);
        outputPanel.appendOutput("Started new game with a " + dimension + "x" + dimension + " board.");
    }

    public boolean insertImage(String filename, int row, int col) throws IOException {
        try {
            if (picPanel.insertImage(filename, row, col)) {
                outputPanel.appendOutput("Inserting " + filename + " in position (" + row + ", " + col + ")");
                return true;
            }
        } catch (PicturePanel.InvalidCoordinateException e) {
            outputPanel.appendOutput(e.toString());
        }
        return false;
    }

    @Override
    public void submitClicked() {
        String userInput = outputPanel.getInputText().trim().toLowerCase();
        JSONObject request = new JSONObject();

        switch (userInput) {
            case "start":
                request.put("action", "start");
                request.put("name", "Player"); // could prompt for actual name
                request.put("duration", "60");
                break;
            case "next":
            case "skip":
            case "remaining":
            case "leaderboard":
            case "quit":
                request.put("action", userInput);
                break;
            default:
                request.put("action", "guess");
                request.put("answer", userInput);
        }

        sendToServer(request);

        if (userInput.equals("quit")) {
            running = false;
            close();
            frame.dispose();
        }
    }

    @Override
    public void inputUpdated(String input) {
        if (input.equals("surprise")) {
            outputPanel.appendOutput("You found me!");
        }
    }

    private void sendToServer(JSONObject request) {
        try {
            os.writeObject(request.toString());
            os.flush();
        } catch (IOException e) {
            outputPanel.appendOutput("Error sending to server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void listenToServer() {
        try {
            while (running) {
                String msg = in.readUTF();
                if (msg == null) break;

                outputPanel.appendOutput("[Server] " + msg);

                // Optional: handle image updates here if server sends them
                // JSONObject json = new JSONObject(msg);
                // if (json.has("image")) insertImage(json.getString("image"), 0, 0);
            }
        } catch (IOException e) {
            if (running) {
                outputPanel.appendOutput("Lost connection to server.");
                e.printStackTrace();
            }
        }
    }

    public void open() throws IOException {
        sock = new Socket(host, port);
        os = new ObjectOutputStream(sock.getOutputStream());
        in = new DataInputStream(sock.getInputStream());
    }

    public void close() {
        try {
            running = false;
            if (os != null) os.close();
            if (in != null) in.close();
            if (sock != null) sock.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            ClientGui gui = new ClientGui("localhost", 8888);
            gui.show(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
