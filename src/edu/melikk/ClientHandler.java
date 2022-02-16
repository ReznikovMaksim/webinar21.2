package edu.melikk;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final EchoServer server;
    private final PrintWriter writer;
    private final Scanner reader;
    private String userName;
    private boolean stopped;

    private ClientHandler(Socket socket, EchoServer server) throws IOException {
        this.socket = socket;
        this.server = server;
        writer = getWriter(socket);
        reader = getReader(socket);
        userName = "";
        stopped = false;
    }

    public static ClientHandler connectClient(Socket socket, EchoServer server) {
        try {
            return new ClientHandler(socket, server);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void run() {
        System.out.printf("The client is connected: %s%n", socket);
        try (socket; reader; writer) {
            logIn();
            while (socket.isConnected() && !stopped) {
                String message = reader.nextLine();
                messageProcessing(message);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        System.out.printf("The client is disconnected: %s%n", this.getSocket());
        server.getClients().remove(this);
    }

    private void logIn() {
        do {
            sendMessage("server: Enter the username (without spaces)");
            changeName(reader.nextLine());
        } while (userName.isEmpty());
        sendBroadcastMessage(String.format("server: User %s joined the chat.", userName));
    }

    private void messageProcessing(String message) throws IOException {
        if (message == null || message.isBlank()) {
            sendMessage("server: Please don't spam.");
            return;
        }
        String firstWord = message.split(" ")[0].toLowerCase();
        String withoutFirstWord = message.replaceFirst(firstWord, "").strip();
        switch (firstWord) {
            case "/exit":
                sendBroadcastMessage(String.format("server: %s has left the chat.", userName));
                this.stopped = true;
            case "/name":
                changeName(withoutFirstWord);
                break;
            case "/list":
                sendListUsers();
                break;
            case "/whisper":
                sendPrivateMessage(withoutFirstWord);
                break;
            default:
                sendBroadcastMessage(String.format("%s: %s", userName, message));
        }
    }

    public void sendMessage(String massage) {
        writer.write(massage);
        writer.write(System.lineSeparator());
        writer.flush();
    }

    private void sendBroadcastMessage(String massage) {
        server.getClients().forEach(c -> {
            if (c != this) c.sendMessage(massage);
        });
    }

    private void sendPrivateMessage(String recipientAndMessage) {
        String recipient = recipientAndMessage.split(" ")[0].toLowerCase();
        if(recipient.equals(userName)){
            sendMessage("server: You're lonely?");
            return;
        }
        String message = recipientAndMessage.replaceFirst(recipient, "").strip();
        var optionalClientHandler = server.getClients().stream()
                .filter(c -> c.getUserName().equalsIgnoreCase(recipient))
                .findFirst();
        optionalClientHandler.ifPresentOrElse(
                c -> c.sendMessage(String.format("Private from %s: %s", userName, message)),
                () -> sendMessage(String.format("server: There is no active user %s.", recipient))
        );
    }

    private void sendListUsers() {
        var listUsers =  server.getClients().stream()
                .map(c -> c.getUserName())
                .collect(Collectors.toList());
        listUsers.sort(String::compareTo);
        sendMessage("server: List of users\n" + listUsers);
    }

    private void changeName(String name) {
        String oldName = userName;
        try {
            if (!name.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("server: Invalid name.");
            if (isTaken(name)) throw new IllegalArgumentException("server: Name taken.");
            userName = name;
            sendMessage("server: You are now known as " + userName);
        } catch (IllegalArgumentException e) {
            sendMessage(e.getMessage());
        }
        if (!oldName.isEmpty() && !oldName.equals(userName)) {
            sendBroadcastMessage(String.format("server: User %s is now known as %s.", oldName, userName));
        }
    }

    private static PrintWriter getWriter(Socket socket) throws IOException {
        OutputStream stream = socket.getOutputStream();
        return new PrintWriter(stream);
    }

    private static Scanner getReader(Socket socket) throws IOException {
        InputStream stream = socket.getInputStream();
        InputStreamReader input = new InputStreamReader(stream, "UTF-8");
        return new Scanner(input);
    }

    private boolean isTaken(String name) {
        //чтоб не обманывали!
        if (name.toLowerCase().contains("server")) return true;
        for (ClientHandler c : server.getClients()) {
            if (name.equalsIgnoreCase(c.getUserName())) {
                return true;
            }
        }
        return false;
    }

    public String getUserName() {
        return userName;
    }

    public Socket getSocket() {
        return socket;
    }
}
