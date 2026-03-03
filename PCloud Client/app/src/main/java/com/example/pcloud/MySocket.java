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
      try {
        socket = new Socket(MySocket.getIP(), MySocket.getPort());
        socket.setSoTimeout(1000);
        MySocket.setSocket(socket);
        MySocket.setOutput(new PrintWriter(socket.getOutputStream()));
        MySocket.setInput(new BufferedReader(new InputStreamReader(socket.getInputStream())));
        ClientLogger.log(
            "ConnectionThread",
            "Connected to server at " + MySocket.getIP() + ":" + MySocket.getPort());
        break;
      } catch (IOException e) {
        ClientLogger.logError(
            "ConnectionThread", "Primary IP connection failed, trying internal IP", e);
        MySocket.setIP(MySocket.getINERIP());
        try {
          socket = new Socket(MySocket.getIP(), MySocket.getPort());
          socket.setSoTimeout(1000);
          MySocket.setSocket(socket);
          MySocket.setOutput(new PrintWriter(socket.getOutputStream()));
          MySocket.setInput(new BufferedReader(new InputStreamReader(socket.getInputStream())));
          ClientLogger.log("ConnectionThread", "Connected via internal IP " + MySocket.getIP());
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

  private static String INERIP = "10.0.2.2";

  private static String IP = "10.0.2.2"; // configured by run.ps1

  private static int Port = 22703;

  private static boolean closed;

  private static String extraMessage = "";

  private static BigInteger G;

  private static byte[] AESkey = {};

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
    String[] sp_mes = mes.split("\n\ndata:");
    String[] headers = sp_mes[0].split("\n");
    this.name = headers[0].substring(5);
    this.type = headers[1].substring(5);
    this.size = Integer.parseInt(headers[2].substring(5));
    this.data = "";
    for (int i = 1; i < sp_mes.length - 1; i++) {
      this.data += sp_mes[i] + "\n\ndata:";
    }
    if (sp_mes.length != 1) {
      this.data += sp_mes[sp_mes.length - 1];
    }
    if (MySocket.getAESkey().length != 0) {
      data = AESEncryptDecrypt.decrypt(data, MySocket.getAESkey());
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
