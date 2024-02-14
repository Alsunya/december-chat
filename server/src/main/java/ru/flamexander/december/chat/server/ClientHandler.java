package ru.flamexander.december.chat.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Set;

public class ClientHandler {
    private Server server;
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private String username;
    private Set<Role> roles;
    private LocalTime enterTime;

    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }

    public ClientHandler(Server server, Socket socket) throws IOException {
        this.server = server;
        this.socket = socket;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
        new Thread(() -> {
            try {
                authentication();
                listenUserChatMessages();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } finally {
                disconnect();
            }
        }).start();
    }

    private void listenUserChatMessages() throws IOException, SQLException {
        while (true) {
                String message = in.readUTF();
                if(Duration.between(enterTime, LocalTime.now()).toMinutes() >= 20) {
                    sendMessage("Вы были не активны более 20 минут. Выполнен выход из чата.");
                    disconnect();
                }
                else if (message.startsWith("/")) {
                    if (message.equals("/exit")) {
                        break;
                    }
                    if (message.startsWith("/w")) {
                        String[] messageElements = message.split(" ", 3);
                        server.sendPrivateMessage(this, messageElements[1], message);
                    }
                    if (message.startsWith("/kick")) {
                        if (this.roles.stream().anyMatch(role -> role.getName().equals("ADMIN"))) {
                            String[] messageElements = message.split(" ");
                            if (messageElements.length != 2) {
                                sendMessage("СЕРВЕР: Некорректная команда");
                            } else {
                                server.kickMember(messageElements[1]);
                            }
                        } else {
                            sendMessage("У вас недостаточно прав для данной операции");
                        }
                    }
                    if (message.startsWith("/changenick")) {
                        String[] messageElements = message.split(" ");
                        if (messageElements.length != 2) {
                            sendMessage("СЕРВЕР: Некорректная команда");
                        } else if (messageElements[1].equals(this.getUsername())) {
                            sendMessage("Этот ник у вас уже установлен");
                        } else {
                            server.changeNick(this, messageElements[1]);
                        }
                    }
                    if (message.startsWith("/activelist")) {
                        sendMessage("Список активных пользователей:" + server.getActiveClients());
                    }
                    if (message.startsWith("/shutdown")) {
                        if (this.roles.stream().anyMatch(role -> role.getName().equals("ADMIN"))) {
                            String[] messageElements = message.split(" ");
                            if (messageElements.length != 1) {
                                sendMessage("СЕРВЕР: Некорректная команда");
                            } else {
                                server.broadcastMessage("Админ остановил чат");
                                server.stop();
                            }
                        } else {
                            sendMessage("У вас недостаточно прав для данной операции");
                        }
                    }
                } else {
                    server.broadcastMessage(ZonedDateTime.now() + " " + this.username + ": " + message);
                }
            }
        }

    public void sendMessage(String message) {
        try {
            out.writeUTF(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(String message, ClientHandler receiver, ClientHandler sender) {
        try {
            receiver.out.writeUTF(sender.username + ": " + message);
            sender.out.writeUTF(sender.username + ": " + message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        server.unsubscribe(this);
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean tryToAuthenticate(String message) {
        String[] elements = message.split(" "); // /auth login1 pass1
        if (elements.length != 3) {
            sendMessage("СЕРВЕР: некорректная команда аутентификации");
            return false;
        }
        String login = elements[1];
        String password = elements[2];
        String usernameFromUserService = server.getUserService().getUsernameByLoginAndPassword(login, password);
        roles = server.getUserService().getUserRoleByLoginAndPassword(login, password);
        if (usernameFromUserService == null) {
            sendMessage("СЕРВЕР: пользователя с указанным логин/паролем не существует");
            return false;
        }
        if (server.isUserBusy(usernameFromUserService)) {
            sendMessage("СЕРВЕР: учетная запись уже занята");
            return false;
        }
        username = usernameFromUserService;
        server.subscribe(this);
        sendMessage("/authok " + username);
        sendMessage("СЕРВЕР: " + username + ", добро пожаловать в чат!");
        return true;
    }

    private boolean register(String message) {
        String[] elements = message.split(" "); // /auth login1 pass1 user1
        if (elements.length != 4) {
            sendMessage("СЕРВЕР: некорректная команда аутентификации");
            return false;
        }
        String login = elements[1];
        String password = elements[2];
        String registrationUsername = elements[3];
        String role = "USER";
        if (server.getUserService().isLoginAlreadyExist(login)) {
            sendMessage("СЕРВЕР: указанный login уже занят");
            return false;
        }
        if (server.getUserService().isUsernameAlreadyExist(registrationUsername)) {
            sendMessage("СЕРВЕР: указанное имя пользователя уже занято");
            return false;
        }
        server.getUserService().createNewUser(login, password, registrationUsername, role);
        username = registrationUsername;
        sendMessage("/authok " + username);
        sendMessage("СЕРВЕР: " + username + ", вы успешно прошли регистрацию, добро пожаловать в чат!");
        server.subscribe(this);
        return true;
    }

    private void authentication() throws IOException {
        while (true) {
            String message = in.readUTF();
            boolean isSucceed = false;
            if (message.startsWith("/auth ")) {
                isSucceed = tryToAuthenticate(message);
            } else if (message.startsWith("/register ")) {
                isSucceed = register(message);
            } else {
                sendMessage("СЕРВЕР: требуется войти в учетную запись или зарегистрироваться");
            }
            if (isSucceed) {
                enterTime = LocalTime.now();
                break;
            }
        }
    }
}