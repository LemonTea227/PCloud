package com.example.pcloud;

public final class ReconnectSession {
  private static String username = "";
  private static String password = "";

  private ReconnectSession() {}

  public static synchronized void setCredentials(String user, String pass) {
    username = user == null ? "" : user;
    password = pass == null ? "" : pass;
  }

  public static synchronized String getUsername() {
    return username;
  }

  public static synchronized String getPassword() {
    return password;
  }

  public static synchronized boolean hasCredentials() {
    return username != null
        && password != null
        && !username.trim().equals("")
        && !password.trim().equals("");
  }

  public static synchronized void clear() {
    username = "";
    password = "";
  }
}