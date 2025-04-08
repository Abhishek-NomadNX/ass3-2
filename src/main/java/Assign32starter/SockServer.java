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
    static int skipsLeft = 3;
    static String duration;

    static boolean isGameRunning = false;
    static Map<String, Integer> allScores = new LinkedHashMap<>();

    static String currentUser = "";
    static int score = 0;

    public static void main(String args[]) {
        Socket sock;

        try {

            //opening the socket here, just hard coded since this is just a bas example
            ServerSocket serv = new ServerSocket(8888); // TODO, should not be hardcoded
            System.out.println("Server ready for connetion");

            // placeholder for the person who wants to play a game
            String name = "";
            int points = 0;

            // read in one object, the message. we know a string was written only by knowing what the client sent.
            // must cast the object from Object to desired type to be useful
            while (true) {
                sock = serv.accept(); // blocking wait
                System.out.println("Client connected");

                // setup the object reading channel
                in = new ObjectInputStream(sock.getInputStream());

                // get output channel
                OutputStream out = sock.getOutputStream();

                // create an object output writer (Java only)
                os = new DataOutputStream(out);

                while(true) {
                    try {
                        String s = (String) in.readObject();
                        System.out.println("Request : " + s);
                        JSONObject req = new JSONObject(s);
                        JSONObject res = handleRequest(req);
                        writeOut(res);
                    } catch (Exception e) {
                        e.printStackTrace();
                        JSONObject error = new JSONObject();
                        error.put("ok", false);
                        error.put("message", "Error processing request");
                        writeOut(error);
                    }
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
                statGame(req);
                res.put("ok", true);
                res.put("img", getCurrentImage());
                res.put("msg", "Game Started");
                break;

            case "guess":
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
                    res.put("img", getCurrentImage());
                } else {
                    res.put("msg", "Game Over! No more movies.");
                }
                break;

            case "next":
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
                res.put("ok", true);
                res.put("msg", "Skips remaining: " + skipsLeft);
                break;

            case "quit":
                res.put("ok", true);
                res.put("msg", "Goodbye " + currentUser + "! Your score: " + score);
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
        } else if(duration.equalsIgnoreCase("medium")) {
            skipsLeft = 4;
        } else {
            skipsLeft = 6;
        }

        currentMovieIndex = 0;
        currentImageIndex = 0;
        score = 0;
        initializeMovies();
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
}
