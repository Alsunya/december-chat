package ru.flamexander.december.chat.server;

import java.util.Set;

public class User {
    private final String login;
    private final String password;
    private String userName;
    private String role;

    @Override
    public String toString() {
        return "User{" +
                "login='" + login + '\'' +
                ", roles=" + roles +
                ", password='" + password + '\'' +
                '}';
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    private Set<Role> roles;

    public String getLogin() {
        return login;
    }

    public String getPassword() {
        return password;
    }

    public String getUserName() {
        return userName;
    }
    public void setUserName(String userName) {
        this.userName = userName;
    }

    public User(String login, String password, String userName, String role) {
        this.login = login;
        this.password = password;
        this.userName = userName;
        this.role = role;
    }

    public User(String login, String password, String userName) {
        this.login = login;
        this.password = password;
        this.userName = userName;
    }
}
