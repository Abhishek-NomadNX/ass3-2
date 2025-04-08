package Assign32starter;

import java.net.*;
import java.util.Base64;
import java.util.Set;
import java.util.Stack;
import java.util.*;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

import java.awt.image.BufferedImage;
import java.io.*;

import org.json.*;


/**
 * A class to demonstrate a simple client-server connection using sockets.
 * Ser321 Foundations of Distributed Software Systems
 */
public class SockServer {
    static Stack<String> imageSource = new Stack<String>();

    static DataOutputStream os;
    static ObjectInputStream in;

    static Map<String, List<String>> movieImages = new LinkedHashMap<>();
    static List<String> movieOrder = new ArrayList<>();
    static int currentMovieIndex = 0;
    static int currentImageIndex = 0;
    static int skipsLeft = 0;
    static String duration;
    static long startTimeMillis = 0;
    static boolean isGameRunning = false;
    static Map<String, Integer> allScores = new LinkedHashMap<>();
    static final String SCORE_FILE = "scores.txt";

    static String currentUser = "";
    static int score = 0;

    public static void main(String args[]) {
        Socket sock = null;

        try {
            loadScores(); // Load leaderboard from file on server start

            ServerSocket serv = new ServerSocket(8888);
            System.out.println("🎮 Server ready for connection on port 8888");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                saveScores();
                System.out.println("Leaderboard saved. Server shutting down.");
            }));

            while (true) {
                try {
                    sock = serv.accept();
                    System.out.println("Client connected");

                    in = new ObjectInputStream(sock.getInputStream());
                    OutputStream out = sock.getOutputStream();
                    os = new DataOutputStream(out);

                    boolean clientActive = true;
                    while (clientActive) {
                        try {
                            String s = (String) in.readObject();
                            System.out.println("Request: " + s);

                            JSONObject req = new JSONObject(s);
                            JSONObject res = handleRequest(req);
                            writeOut(res);
                        } catch (EOFException | SocketException clientDisconnected) {
                            System.out.println("Client disconnected.");
                            clientActive = false; // exit inner loop
                        } catch (Exception e) {
                            e.printStackTrace();
                            JSONObject error = new JSONObject();
                            error.put("ok", false);
                            error.put("message", "Error processing request");
                            writeOut(error);
                        }
                    }

                    // Clean up this client's resources
                    try {
                        if (in != null) in.close();
                        if (os != null) os.close();
                        if (sock != null) sock.close();
                    } catch (IOException ioClose) {
                        System.out.println("Error closing client socket: " + ioClose.getMessage());
                    }
                } catch (IOException acceptEx) {
                    System.out.println("Failed to accept client: " + acceptEx.getMessage());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    static JSONObject handleRequest(JSONObject req) throws Exception {
        String action = req.getString("action");
        JSONObject res = new JSONObject();

        switch (action) {
            case "start":
                if (isGameRunning) {
                    res.put("ok", false);
                    res.put("msg", "A game is already in progress. Please quit first.");
                } else {
                    statGame(req);
                    isGameRunning = true;
                    res.put("ok", true);
                    res.put("img", getCurrentImage());
                    res.put("msg", "Game Started");
                }
                break;

            case "guess":
                if (!validateGameInProgress(res)) break;

                String userGuess = req.getString("answer").toLowerCase();
                String correctMovie = movieOrder.get(currentMovieIndex).toLowerCase();
                if (userGuess.equals(correctMovie)) {
                    score++;
                    allScores.put(currentUser, score); // Update score in leaderboard
                    res.put("ok", true);
                    res.put("msg", "Correct! Moving to next movie.");
                    currentMovieIndex++;
                    currentImageIndex = 0;
                } else {
                    res.put("ok", false);
                    res.put("msg", "Wrong guess. Try again or type 'next' for clearer image.");
                }

                if (currentMovieIndex < movieOrder.size()) {
                    res.put("img", getCurrentImage());
                } else {
                    res.put("msg", "Game Over! No more movies.");
                }
                break;

            case "next":
                if (!validateGameInProgress(res)) break;

                currentImageIndex++;
                if (currentImageIndex >= 4) {
                    currentImageIndex = 3;
                    res.put("ok", false);
                    res.put("msg", "This is the clearest image already.");
                } else {
                    res.put("ok", true);
                    res.put("msg", "Here is a clearer image.");
                }
                res.put("img", getCurrentImage());
                break;

            case "skip":
                if (!validateGameInProgress(res)) break;

                if (skipsLeft > 0) {
                    skipsLeft--;
                    currentMovieIndex++;
                    currentImageIndex = 0;
                    res.put("ok", true);
                    res.put("msg", "Movie skipped.");
                    if (currentMovieIndex < movieOrder.size()) {
                        res.put("img", getCurrentImage());
                    } else {
                        res.put("msg", "Game Over! No more movies.");
                    }
                } else {
                    res.put("ok", false);
                    res.put("msg", "No skips left!");
                }
                break;

            case "remaining":
                if (!validateGameInProgress(res)) break;

                res.put("ok", true);
                res.put("msg", "Skips remaining: " + skipsLeft);
                break;

            case "quit":
                if (!isGameRunning) {
                    res.put("ok", false);
                    res.put("msg", "No game in progress to quit.");
                    break;
                }

                int previous = allScores.getOrDefault(currentUser, 0);
                if (score > previous) {
                    allScores.put(currentUser, score);
                }

                res.put("ok", true);
                res.put("msg", "Goodbye " + currentUser + "! Your score: " + score + ". You can now start a new game.");
                allScores.put(currentUser, score);
                isGameRunning = false;
                break;

            case "scores":
                res.put("ok", true);
                res.put("msg", "All Scores");
                JSONObject scoresObj = new JSONObject();
                for (Map.Entry<String, Integer> entry : allScores.entrySet()) {
                    scoresObj.put(entry.getKey(), entry.getValue());
                }
                res.put("scores", scoresObj);
                break;

            default:
                res.put("ok", false);
                res.put("msg", "Unknown command.");
                break;
        }


        return res;
    }

    static void statGame(JSONObject req) {
        currentUser = req.getString("name");
        duration = req.getString("duration");

        if(duration.equalsIgnoreCase("short")) {
            skipsLeft = 2;
            startTimeMillis = System.currentTimeMillis() + 30 * 1000;
        } else if(duration.equalsIgnoreCase("medium")) {
            skipsLeft = 4;
            startTimeMillis = System.currentTimeMillis() + 60 * 1000;
        } else {
            skipsLeft = 6;
            startTimeMillis = System.currentTimeMillis() + 90 * 1000;
        }

        currentMovieIndex = 0;
        currentImageIndex = 0;
        score = 0;
        initializeMovies();

        // Add player to leaderboard with 0 score
        allScores.put(currentUser, 0);
    }

    static void initializeMovies() {
        movieImages.put("Inception", Arrays.asList("inception1.png", "inception2.png", "inception3.png", "inception4.png"));
        movieImages.put("Titanic", Arrays.asList("titanic1.png", "titanic2.png", "titanic3.png", "titanic4.png"));
        movieImages.put("Matrix", Arrays.asList("matrix1.png", "matrix2.png", "matrix3.png", "matrix4.png"));
        movieImages.put("Avatar", Arrays.asList("avatar1.png", "avatar2.png", "avatar3.png", "avatar4.png"));
        movieImages.put("Interstellar", Arrays.asList("interstellar1.png", "interstellar2.png", "interstellar3.png", "interstellar4.png"));

        // Create a temporary list of keys, shuffle, then add to movieOrder
        List<String> movieList = new ArrayList<>(movieImages.keySet());
        Collections.shuffle(movieList); // Randomize the order
        movieOrder.addAll(movieList);
    }

    static String getCurrentImage() {
        if (currentMovieIndex >= movieOrder.size()) return "none";
        String movie = movieOrder.get(currentMovieIndex);
        return movieImages.get(movie).get(currentImageIndex);
    }

    static boolean validateGameInProgress(JSONObject res) {
        if (!isGameRunning) {
            res.put("ok", false);
            res.put("msg", "No game in progress. Please start a game first.");
            return false;
        }

        if (isTimeOver()) {
            isGameRunning = false;
            allScores.put(currentUser, score);
            res.put("ok", false);
            res.put("msg", "Time Over! Game ended. Your score: " + score);
            return false;
        }

        return true;
    }
    /* TODO this is for you to implement, I just put a place holder here */
    public static JSONObject sendImg(String filename, JSONObject obj) throws Exception {
        File file = new File(filename);

        if (file.exists()) {
            // import image
            // I did not use the Advanced Custom protocol
            // I read in the image and translated it into basically into a string and send it back to the client where I then decoded again
            obj.put("image", "Pretend I am this image: " + filename);
        }
        return obj;
    }


    // sends the response and closes the connection between client and server.
    static void writeOut(JSONObject res) {
        try {
            os.writeUTF(res.toString());
            // make sure it wrote and doesn't get cached in a buffer
            os.flush();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    static boolean isTimeOver() {
        return System.currentTimeMillis() > startTimeMillis;
    }

    static void loadScores() {
        File file = new File(SCORE_FILE);
        if (!file.exists()) {
            System.out.println("Leaderboard file not found. Creating new one.");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    String name = parts[0].trim();
                    int score = Integer.parseInt(parts[1].trim());
                    allScores.put(name, score);
                }
            }
            System.out.println("Leaderboard loaded successfully.");
        } catch (Exception e) {
            System.out.println("Failed to load leaderboard.");
            e.printStackTrace();
        }
    }

    static void saveScores() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(SCORE_FILE))) {
            for (Map.Entry<String, Integer> entry : allScores.entrySet()) {
                writer.write(entry.getKey() + ":" + entry.getValue());
                writer.newLine();
            }
        } catch (IOException e) {
            System.out.println("Error writing leaderboard to file.");
            e.printStackTrace();
        }
    }


}
