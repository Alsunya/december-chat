package ru.flamexander.december.chat.server;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JDBCUserService implements UserService {
    static final String DATABASE_URL = "jdbc:postgresql://localhost:5432/postgres";

    private static final String SELECT_USERS_SQL = "SELECT u.login, u.password, u.user_name FROM Users u";
    private static final String SELECT_ROLES_FOR_USER = "SELECT r.id, r.name " +
            "from user_to_role left join roles r on r.id = user_to_role.role_id where user_id = ?";
    static final String UPDATE_USERNAME = "UPDATE users SET user_name = ? WHERE user_name = ?";
    static List<User> users = new ArrayList<>();

    public void startService() {
        try (Connection connection = DriverManager.getConnection(DATABASE_URL, "alsu", "postgres")) {
            try (Statement statement = connection.createStatement()) {
                try (ResultSet resultSet = statement.executeQuery(SELECT_USERS_SQL)) {
                    while (resultSet.next()) {
                        String login = resultSet.getString(1);
                        String password = resultSet.getString(2);
                        String userName = resultSet.getString(3);
                        User user = new User(login, password, userName);
                        try (PreparedStatement preparedStatement = connection.prepareStatement(SELECT_ROLES_FOR_USER)) {
                            preparedStatement.setString(1, userName);
                            try (ResultSet rs = preparedStatement.executeQuery()) {
                                Set<Role> roles = new HashSet<>();
                                while (rs.next()) {
                                    Integer id = rs.getInt(1);
                                    String name = rs.getString(2);
                                    Role role = new Role(id, name);
                                    roles.add(role);
                                }
                                user.setRoles(roles);
                            }
                        }
                        users.add(user);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getUsernameByLoginAndPassword(String login, String password) {
        for (User u : users) {
            if (u.getLogin().equals(login) && u.getPassword().equals(password)) {
                return u.getUserName();
            }
        }
        return null;
    }

    @Override
    public void createNewUser(String login, String password, String username, String role) {
        users.add(new User(login, password, username, role));
    }

    @Override
    public boolean isLoginAlreadyExist(String login) {
        for (User u : users) {
            if (u.getLogin().equals(login)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isUsernameAlreadyExist(String username) {
        for (User u : users) {
            if (u.getUserName().equals(username)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<Role> getUserRoleByLoginAndPassword(String login, String password) {
        for (User u : users) {
            if (u.getLogin().equals(login) && u.getPassword().equals(password)) {
                return u.getRoles();
            }
        }
        return null;
    }
}
