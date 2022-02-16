package edu.melikk;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EchoServer {
    private final int port;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private Set<ClientHandler> clients;

    private EchoServer(int port) {
        this.port = port;
        clients = new HashSet<>();
    }

    static EchoServer bindToPort(int port) {
        return new EchoServer(port);
    }

    public void run() {
        System.out.printf("Server is running on the port: %s%n", port);
        try (var server = new ServerSocket(port)) {
            while (!server.isClosed()) {
                Socket clientSocket = server.accept();
                var client = ClientHandler.connectClient(clientSocket, this);
                if (client != null) {
                    clients.add(client);
                    pool.submit(client);
                }
            }
        } catch (IOException ex) {
            var msg = "port is busy";
            System.out.println(msg);
            ex.printStackTrace();
        }
    }

    public Set<ClientHandler> getClients() {
        return clients;
    }

}