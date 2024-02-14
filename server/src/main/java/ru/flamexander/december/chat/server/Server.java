package ru.flamexander.december.chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static ru.flamexander.december.chat.server.JDBCUserService.*;

public class Server {
    private int port;
    private List<ClientHandler> clients;
    private UserService userService;
    private ServerSocket serverSocket;

    public UserService getUserService() {
        return userService;
    }

    public Server(int port) {
        this.port = port;
        this.clients = new ArrayList<>();
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.printf("Сервер запущен на порту %d. Ожидание подключения клиентов\n", port);
            userService = new JDBCUserService();
            userService.startService();
            System.out.println("Запущен сервис для работы с пользователями");
            while (true) {
                Socket socket = serverSocket.accept();
                try {
                    new ClientHandler(this, socket);
                } catch (IOException e) {
                    System.out.println("Не удалось подключить клиента");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void broadcastMessage(String message) {
        for (ClientHandler clientHandler : clients) {
            clientHandler.sendMessage(message);
        }
    }

    public synchronized void subscribe(ClientHandler clientHandler) {
        broadcastMessage("Подключился новый клиент " + clientHandler.getUsername());
        clients.add(clientHandler);
    }

    public synchronized void unsubscribe(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        broadcastMessage("Отключился клиент " + clientHandler.getUsername());
    }

    public synchronized boolean isUserBusy(String username) {
        for (ClientHandler c : clients) {
            if (c.getUsername().equals(username)) {
                return true;
            }
        }
        return false;
    }

    public synchronized void sendPrivateMessage(ClientHandler sender, String receiverUsername, String message) {
        for (ClientHandler client : clients) {
            if (client.getUsername().equals(receiverUsername)) {
                client.sendMessage(message, client, sender);
            }
        }
    }

    public synchronized void kickMember(String userToBeKicked) {
        if (isUserBusy(userToBeKicked)) {
            ClientHandler clientHandler = clients.stream().filter(c -> c.getUsername().equals(userToBeKicked)).findFirst().get();
            broadcastMessage("СЕРВЕР: Админ заблокировал пользователя с ником '" + userToBeKicked + "'");
            clientHandler.sendMessage("Вы заблокированы");
            clientHandler.disconnect();
        }
    }

    public synchronized void changeNick(ClientHandler client, String newUsername) {
        if (this.getUserService().isUsernameAlreadyExist(newUsername)) {
            client.sendMessage("Этот ник уже занят");
        } else {
            try (Connection connection = DriverManager.getConnection(DATABASE_URL, "alsu", "postgres")) {
                try (PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_USERNAME)) {
                    preparedStatement.setString(1, newUsername);
                    preparedStatement.setString(2, client.getUsername());
                    preparedStatement.executeUpdate();
                    for (User user : users) {
                        if (user.getUserName().equals(client.getUsername())) {
                            broadcastMessage("СЕРВЕР: Пользователь " + client.getUsername() + " сменил ник на " + newUsername);
                            user.setUserName(newUsername);
                            client.setUsername(newUsername);
                            break;
                        }
                    }
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized StringBuilder getActiveClients() {
        StringBuilder clientsNicks = new StringBuilder();
        for (ClientHandler client : clients) {
            clientsNicks.append(" | ");
            clientsNicks.append(client.getUsername());
        }
        return clientsNicks;
    }

    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();

                List<ClientHandler> copyOfClients = new ArrayList<>(clients);

                for (ClientHandler client : copyOfClients) {
                    client.disconnect();
                }
                System.out.println("Сервер остановлен");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
