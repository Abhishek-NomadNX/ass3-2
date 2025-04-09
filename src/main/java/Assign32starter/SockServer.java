package Assign32starter;

import java.net.*;
import java.util.Stack;
import java.util.*;

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
    static Map<String, Integer> leaderboard = new LinkedHashMap<>();
    static final String SCORE_FILE = "leaderboard.txt";

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
            case "hello":
                res.put("message", "Ping from server");
                break;
            case "start":
                if (isGameRunning) {
                    res.put("ok", false);
                    res.put("msg", "A game is already in progress. Please quit first.");
                } else {
                    statGame(req);
                    res.put("ok", true);
                    res.put("image", getCurrentImage());
                    res.put("msg", "Game Started");
                }
                break;

            case "guess":
                if (!validateGameInProgress(res)) break;

                String userGuess = req.getString("answer").toLowerCase();
                String correctMovie = movieOrder.get(currentMovieIndex).toLowerCase();
                if (userGuess.equals(correctMovie)) {
                    score++;
                    res.put("ok", true);
                    res.put("msg", "Correct! Moving to next movie.");
                    currentMovieIndex++;
                    currentImageIndex = 0;
                } else {
                    res.put("ok", false);
                    res.put("msg", "Wrong guess. Try again or type 'next' for clearer image.");
                }

                if (currentMovieIndex < movieOrder.size()) {
                    res.put("image", getCurrentImage());
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
                res.put("image", getCurrentImage());
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
                        res.put("image", getCurrentImage());
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

                res.put("ok", true);
                res.put("msg", "Goodbye " + currentUser + "! Your score: " + score);
                endGame();
                break;

            case "leaderboard":
                res.put("ok", true);
                res.put("msg", "All Scores");
                JSONObject scoresObj = new JSONObject();
                for (Map.Entry<String, Integer> entry : leaderboard.entrySet()) {
                    scoresObj.put(entry.getKey(), entry.getValue());
                }
                res.put("leaderboard", scoresObj);
                break;

            default:
                res.put("ok", false);
                res.put("msg", "Unknown command.");
                break;
        }


        return res;
    }

    static void statGame(JSONObject req) {
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
        isGameRunning = true;
        score = 0;
        initializeMovies();

    }

    static void endGame() {
        if (!currentUser.isEmpty()) {
            int previous = leaderboard.getOrDefault(currentUser, 0);
            if (score > previous) {
                leaderboard.put(currentUser, score);
                System.out.println("Updated score for " + currentUser + ": " + score);
            } else {
                System.out.println(currentUser + " finished with score: " + score + " (not a high score)");
            }
        }

        // Reset game state
        currentUser = "";
        score = 0;
        currentMovieIndex = 0;
        currentImageIndex = 0;

        skipsLeft = 0;
        movieOrder.clear();

        isGameRunning = false;

        saveScores(); // save to file
    }


    static void initializeMovies() {
        movieImages.put("BackToTheFuture", Arrays.asList(
                "BackToTheFuture1.png", "BackToTheFuture2.png", "BackToTheFuture3.png", "BackToTheFuture4.png"));

        movieImages.put("JurassicPark", Arrays.asList(
                "JurassicPark1.png", "JurassicPark2.png", "JurassicPark3.png", "JurassicPark4.png"));

        movieImages.put("LordOfTheRings", Arrays.asList(
                "LordOfTheRings1.png", "LordOfTheRings2.png", "LordOfTheRings3.png", "LordOfTheRings4.png"));

        movieImages.put("TheDarkKnight", Arrays.asList(
                "TheDarkKnight1.png", "TheDarkKnight2.png", "TheDarkKnight3.png", "TheDarkKnight4.png"));

        movieImages.put("TheLionKing", Arrays.asList(
                "TheLionKing1.png", "TheLionKing2.png", "TheLionKing3.png", "TheLionKing4.png"));

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
            res.put("ok", false);
            res.put("msg", "Time Over! Game ended. Your score: " + score);
            endGame();
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
                    leaderboard.put(name, score);
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
            for (Map.Entry<String, Integer> entry : leaderboard.entrySet()) {
                writer.write(entry.getKey() + ":" + entry.getValue());
                writer.newLine();
            }
        } catch (IOException e) {
            System.out.println("Error writing leaderboard to file.");
            e.printStackTrace();
        }
    }


}
