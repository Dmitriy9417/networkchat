package ru.netology.server;


import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private int port;
    private Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private ServerSocket serverSocket;
    private volatile boolean isRunning;
    private File logFile;
    private List<Thread> clientThreads;

    public ChatServer(int port) {
        this.port = port;
        this.clientThreads = new ArrayList<>();
        this.logFile = new File("server.log");
    }

    public void start() {
        isRunning = true;
        try {
            serverSocket = new ServerSocket(port);
            log("Server started on port " + port);

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                    clients.add(clientHandler);

                    Thread clientThread = new Thread(clientHandler);
                    clientThreads.add(clientThread);
                    clientThread.start();
                } catch (SocketException e) {
                    if (isRunning) {
                        log("Server socket error: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log("Server error: " + e.getMessage());
        } finally {
            stop();
        }
    }

    public void stop() {
        isRunning = false;
        try {
            // Закрываем все клиентские соединения
            for (ClientHandler client : clients) {
                client.disconnect();
            }

            // Ожидаем завершения всех клиентских потоков
            for (Thread thread : clientThreads) {
                try {
                    thread.join(1000); // Даем потокам 1 секунду на завершение
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log("Error closing server: " + e.getMessage());
        }
        log("Server stopped");
    }

    public void broadcast(String message, ClientHandler sender) {
        String formattedMessage = "[" + new Date() + "] " + sender.getClientName() + ": " + message;
        logToFile(formattedMessage);

        for (ClientHandler client : clients) {
            if (client != sender) {
                client.sendMessage(formattedMessage);
            }
        }
    }

    public void removeClient(ClientHandler client) {
        clients.remove(client);
        log("Client " + client.getClientName() + " disconnected");
    }

    protected void log(String message) {
        System.out.println("[SERVER] " + message);
    }

    private void logToFile(String message) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            writer.println(message);
        } catch (IOException e) {
            log("Error writing to log file: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        int port = readPortFromSettings("settings.txt");
        ChatServer server = new ChatServer(port);
        server.start();
    }

    private static int readPortFromSettings(String filename) {
        try (Scanner scanner = new Scanner(new File(filename))) {
            if (scanner.hasNextLine()) {
                return Integer.parseInt(scanner.nextLine().trim());
            }
        } catch (FileNotFoundException e) {
            System.out.println("Settings file not found, using default port 8080");
        } catch (NumberFormatException e) {
            System.out.println("Invalid port number in settings, using default port 8080");
        }
        return 8080;
    }
}
