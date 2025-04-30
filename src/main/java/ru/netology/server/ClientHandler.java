package ru.netology.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {
    private static final int INPUT_BUFFER_SIZE = 8192;

    private final Socket clientSocket;
    private final ChatServer server;
    private PrintWriter out;
    private InputStream rawIn;
    private String clientName;
    private final File logFile;
    private volatile boolean running;

    public ClientHandler(Socket socket, ChatServer server) {
        this.clientSocket = socket;
        this.server = server;
        this.logFile = new File("client_" + socket.getPort() + ".log");
        this.running = true;
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            rawIn = clientSocket.getInputStream();

            // Get client name
            sendPrompt("Enter your name:");
            this.clientName = readLineFromStream();

            if (clientName == null || clientName.trim().isEmpty()) {
                clientName = "Anonymous_" + clientSocket.getPort();
            }

            server.log("Client " + clientName + " connected");
            sendPrompt("Welcome, " + clientName + "! Type '/exit' to quit.");

            // Main message loop
            String inputLine;
            while (running && (inputLine = readLineFromStream()) != null) {
                if ("/exit".equalsIgnoreCase(inputLine.trim())) {
                    break;
                }
                if (!inputLine.trim().isEmpty()) {
                    server.broadcast(inputLine.trim(), this);
                }
            }
        } catch (IOException e) {
            server.log("Error with client " + (clientName != null ? clientName : "unknown") + ": " + e.getMessage());
        } finally {
            disconnect();
            server.removeClient(this);
        }
    }

    private String readLineFromStream() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[INPUT_BUFFER_SIZE];

        while (running) {
            int bytesRead = rawIn.read(buffer);
            if (bytesRead == -1) return null;

            for (int i = 0; i < bytesRead; i++) {
                byte b = buffer[i];
                if (b == '\n' || b == '\r') {
                    // Return what we have so far
                    String line = baos.toString(StandardCharsets.UTF_8.name());
                    baos.reset();
                    return line.trim();
                }
                baos.write(b);
            }

            // Если не было символа новой строки, проверим через небольшой таймаут
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    public void sendMessage(String message) {
        if (out != null && !out.checkError()) {
            out.println(message);
            out.flush();
            logToFile("Sent: " + message);
        }
    }

    private void sendPrompt(String message) {
        sendMessage(message);
        logToFile("Prompt: " + message);
    }

    public void disconnect() {
        running = false;
        try {
            if (out != null) out.close();
            if (rawIn != null) rawIn.close();
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            server.log("Error disconnecting client: " + e.getMessage());
        }
    }

    public String getClientName() {
        return clientName;
    }

    private void logToFile(String message) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            writer.println("[" + new Date() + "] " + message);
        } catch (IOException e) {
            System.err.println("Error writing to client log file: " + e.getMessage());
        }
    }
}