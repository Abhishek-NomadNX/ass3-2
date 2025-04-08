package Assign32starter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.*;
import java.io.*;
import java.util.Scanner;

/**
 *
 */
class SockClient {
    static Socket sock = null;
    static String host = "localhost";
    static int port = 8888;
    static OutputStream out;
    // Using and Object Stream here and a Data Stream as return. Could both be the same type I just wanted
    // to show the difference. Do not change these types.
    static ObjectOutputStream os;
    static DataInputStream in;

    public static void main(String args[]) {

        if (args.length != 2) {
            System.out.println("Expected arguments: <host(String)> <port(int)>");
            System.exit(1);
        }

        try {
            host = args[0];
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException nfe) {
            System.out.println("[Port|sleepDelay] must be an integer");
            System.exit(2);
        }

        try {
            connect(host, port); // connecting to server
            System.out.println("Client connected to server.");
            boolean requesting = true;
            Scanner scanner = new Scanner(System.in);
            while (requesting) {
                System.out.println("\nChoose action:");
                System.out.println("1 - start\n2 - guess\n3 - next\n4 - skip\n5 - remaining\n0 - quit");
                int choice = Integer.parseInt(scanner.nextLine());
                JSONObject request = new JSONObject();

                switch (choice) {
                    case 1:
                        System.out.println("Enter your name");
                        String username = scanner.nextLine();

                        System.out.println("Enter duration");
                        String duration = scanner.nextLine();

                        request.put("action", "start");
                        request.put("name", username);
                        request.put("duration", duration);

                        break;
                    case 2:
                        System.out.println("Enter your movie guess: ");
                        String guess = scanner.nextLine();

                        request.put("action", "guess");
                        request.put("answer", guess);
                        break;
                    case 3:
                        request.put("action", "next");
                        break;
                    case 4:
                        request.put("action", "skip");
                        break;
                    case 5:
                        request.put("action", "remaining");
                        break;
                    case 6:
                        request.put("action", "scores");
                        break;
                    case 0:
                        request.put("action", "quit");
                        break;
                    default:
                        System.out.println("Invalid option. Try again.");
                        continue;
                }
                // Send request
                System.out.println(request);
                os.writeObject(request.toString());
                os.flush();


                // Receive response
                String responseStr = in.readUTF();

                System.out.println(responseStr);

                if (!requesting) {
                    System.out.println("Game ended. Thanks for playing!");
                }
            }

        } catch (Exception e) {
            System.out.println("Error e");
            e.printStackTrace();
        }
    }


    public static void connect(String host, int port) throws IOException {
        // open the connection
        sock = new Socket(host, port); // connect to host and socket on port 8888

        // get output channel
        out = sock.getOutputStream();

        // create an object output writer (Java only)
        os = new ObjectOutputStream(out);

        in = new DataInputStream(sock.getInputStream());
    }
}