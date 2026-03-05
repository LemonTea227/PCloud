package com.example.pcloud;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.Socket;

class ConnectionThread implements Runnable {

  @Override
  public void run() {
    Socket socket;
    ClientLogger.log(
        "ConnectionThread",
        "Starting connection loop to " + MySocket.getIP() + ":" + MySocket.getPort());
    while (true) {
      String primaryIp = MySocket.getIP();
      try {
        socket = new Socket(primaryIp, MySocket.getPort());
        socket.setSoTimeout(1000);
        MySocket.setSocket(socket);
        MySocket.setOutput(new PrintWriter(socket.getOutputStream()));
        MySocket.setInput(new BufferedReader(new InputStreamReader(socket.getInputStream())));
        MySocket.markActivity();
        ClientLogger.log(
            "ConnectionThread", "Connected to server at " + primaryIp + ":" + MySocket.getPort());
        break;
      } catch (IOException e) {
        ClientLogger.logError(
            "ConnectionThread", "Primary IP connection failed, trying internal IP", e);
        String fallbackIp = MySocket.getINERIP();
        try {
          socket = new Socket(fallbackIp, MySocket.getPort());
          socket.setSoTimeout(1000);
          MySocket.setSocket(socket);
          MySocket.setOutput(new PrintWriter(socket.getOutputStream()));
          MySocket.setInput(new BufferedReader(new InputStreamReader(socket.getInputStream())));
          MySocket.markActivity();
          ClientLogger.log("ConnectionThread", "Connected via internal IP " + fallbackIp);
          break;
        } catch (IOException err) {
          ClientLogger.logError("ConnectionThread", "Internal IP connection failed", err);
        }
      }
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        ClientLogger.logError("ConnectionThread", "Interrupted while reconnecting", e);
      }
    }
  }
}

public class MySocket {
  private static Socket socket;

  private static PrintWriter output;

  private static BufferedReader input;

  private static String INERIP = "192.168.0.7";private static String IP = "192.168.0.7"; // configured by set-phone-host.ps1

  private static int Port = 22703;

  private static boolean closed;

  private static String extraMessage = "";

  private static BigInteger G;

  private static byte[] AESkey = {};

  private static long lastActivityAtMs = System.currentTimeMillis();

  private static int activeTransfers = 0;

  public static final long INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000L;

  public static BigInteger getG() {
    return G;
  }

  public static void setG(BigInteger g) {
    G = g;
  }

  public static byte[] getAESkey() {
    return AESkey;
  }

  public static void setAESkey(byte[] AESkey) {
    MySocket.AESkey = AESkey;
  }

  public static String getExtraMessage() {
    return extraMessage;
  }

  public static void setExtraMessage(String message) {
    MySocket.extraMessage = message;
  }

  public static synchronized boolean isClosed() {
    return closed;
  }

  public static synchronized void setClosed(boolean closed) {
    MySocket.closed = closed;
  }

  public static synchronized void markActivity() {
    lastActivityAtMs = System.currentTimeMillis();
  }

  public static synchronized long getLastActivityAtMs() {
    return lastActivityAtMs;
  }

  public static synchronized void beginTransfer() {
    activeTransfers += 1;
    markActivity();
  }

  public static synchronized void endTransfer() {
    if (activeTransfers > 0) {
      activeTransfers -= 1;
    }
    markActivity();
  }

  public static synchronized boolean hasActiveTransfer() {
    return activeTransfers > 0;
  }

  public static synchronized boolean isInactivityTimedOut() {
    if (hasActiveTransfer()) {
      return false;
    }
    long idleMs = System.currentTimeMillis() - lastActivityAtMs;
    return idleMs >= INACTIVITY_TIMEOUT_MS;
  }

  public static synchronized void disconnect() {
    try {
      if (socket != null) {
        socket.close();
      }
    } catch (IOException ignored) {
    }
    socket = null;
    input = null;
    output = null;
    extraMessage = "";
    closed = true;
    activeTransfers = 0;
  }

  public static synchronized PrintWriter getOutput() {
    return output;
  }

  public static synchronized void setOutput(PrintWriter output) {
    MySocket.output = output;
  }

  public static synchronized BufferedReader getInput() {
    return input;
  }

  public static synchronized void setInput(BufferedReader input) {
    MySocket.input = input;
  }

  public static String getINERIP() {
    return INERIP;
  }

  public static void setIP(String IP) {
    MySocket.IP = IP;
  }

  public static String getIP() {
    return IP;
  }

  public static int getPort() {
    return Port;
  }

  public static synchronized Socket getSocket() {
    return socket;
  }

  public static synchronized void setSocket(Socket sock) {
    socket = sock;
  }

  public static String buildMessage(String name, String type, String data) {
    if (MySocket.getAESkey().length != 0) {
      data = AESEncryptDecrypt.encrypt(data, MySocket.getAESkey());
    }
    int dataLength = data == null ? 0 : data.length();
    return String.format("name:%s\ntype:%s\nsize:%d\n\ndata:%s", name, type, dataLength, data);
  }

  public static boolean doAdd(String mes) {
    int cnt = 0;
    for (int i = mes.length() - 1; i >= 0; i--) {
      if (mes.charAt(i) == '\n') cnt++;
      else break;
    }
    if (cnt == 0 || cnt % 2 == 1) return true;
    return false;
  }
}

class MessageCodes {
  private static String Request = "0";

  private static String Confirm = "1";

  private static String LoginError = "200";

  private static String RegisterError = "201";

  private static String AlbumsError = "202";

  private static String NewAlbumError = "203";

  private static String DelAlbumError = "204";

  private static String PhotosError = "205";

  private static String UploadPhotoError = "206";

  private static String PhotoError = "207";

  private static String DelPhotosError = "208";

  private static String AccessDenied = "3";

  public static String getRequest() {
    return Request;
  }

  public static String getConfirm() {
    return Confirm;
  }

  public static String getLoginError() {
    return LoginError;
  }

  public static String getRegisterError() {
    return RegisterError;
  }

  public static String getAlbumsError() {
    return AlbumsError;
  }

  public static String getNewAlbumError() {
    return NewAlbumError;
  }

  public static String getDelAlbumError() {
    return DelAlbumError;
  }

  public static String getPhotosError() {
    return PhotosError;
  }

  public static String getUploadPhotoError() {
    return UploadPhotoError;
  }

  public static String getPhotoError() {
    return PhotoError;
  }

  public static String getDelPhotosError() {
    return DelPhotosError;
  }

  public static String getAccessDenied() {
    return AccessDenied;
  }
}

class HandelMessage {
  private String name;
  private String type;
  private int size;
  private String data;

  public HandelMessage(String mes) {
    this.name = "";
    this.type = "";
    this.size = 0;
    this.data = "";

    if (mes == null || mes.equals("")) {
      return;
    }

    try {
      String[] sp_mes = mes.split("\n\ndata:", -1);
      if (sp_mes.length == 0) {
        return;
      }

      String[] headers = sp_mes[0].split("\n");
      if (headers.length >= 1 && headers[0].startsWith("name:")) {
        this.name = headers[0].substring(5);
      }
      if (headers.length >= 2 && headers[1].startsWith("type:")) {
        this.type = headers[1].substring(5);
      }
      if (headers.length >= 3 && headers[2].startsWith("size:")) {
        try {
          this.size = Integer.parseInt(headers[2].substring(5));
        } catch (NumberFormatException ignored) {
          this.size = 0;
        }
      }

      StringBuilder builder = new StringBuilder();
      for (int i = 1; i < sp_mes.length; i++) {
        if (i > 1) {
          builder.append("\n\ndata:");
        }
        builder.append(sp_mes[i]);
      }
      this.data = builder.toString();

      if (MySocket.getAESkey().length != 0 && !this.data.equals("")) {
        String decrypted = AESEncryptDecrypt.decrypt(this.data, MySocket.getAESkey());
        this.data = decrypted == null ? "" : decrypted;
      }
      if (this.data == null) {
        this.data = "";
      }
    } catch (Exception ignored) {
      this.name = "";
      this.type = "";
      this.size = 0;
      this.data = "";
    }
  }

  public String getName() {
    return name;
  }

  public String getType() {
    return type;
  }

  public int getSize() {
    return size;
  }

  public String getData() {
    return data;
  }
}
