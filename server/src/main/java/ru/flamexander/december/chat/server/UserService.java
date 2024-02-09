package ru.flamexander.december.chat.server;

import java.util.Set;

public interface UserService {
    String getUsernameByLoginAndPassword(String login, String password);
    void createNewUser(String login, String password, String username, String role);
    boolean isLoginAlreadyExist(String login);
    boolean isUsernameAlreadyExist(String username);
    Set<Role> getUserRoleByLoginAndPassword(String login, String password);
    void startService();
}
