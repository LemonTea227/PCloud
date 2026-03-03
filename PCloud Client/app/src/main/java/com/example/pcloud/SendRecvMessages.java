package com.example.pcloud;

import android.app.Activity;
import android.content.Intent;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.SocketTimeoutException;

class ReceiveMessagesThread implements Runnable {
  private static Activity activity;
  private static ReceiveMessagesListener listener;

  public static synchronized void setListener(ReceiveMessagesListener listener) {
    ReceiveMessagesThread.listener = listener;
  }

  public static synchronized void setActivity(Activity activity) {
    ReceiveMessagesThread.activity = activity;
  }

  @Override
  public void run() {
    ClientLogger.log(
        "ReceiveMessagesThread",
        "Receiver loop started for " + activity.getClass().getSimpleName());
    while (!MySocket.isClosed()) {
      try {
        BufferedReader input = MySocket.getInput();
        if (input == null) {
          try {
            Thread.sleep(50);
          } catch (InterruptedException ignored) {
          }
          continue;
        }
        String m = MySocket.getExtraMessage();
        String d = "";
        int size = -1;
        int length = -2;
        char[] buffer = new char[1024];

        while (!m.contains("\n\n") || length < size) {
          try {
            if (m.equals("") || !m.contains("\n\n") || (length < size && size != -1)) {
              int charsRead = input.read(buffer);
              if (charsRead == -1) continue;
              m += new String(buffer).substring(0, charsRead);
            }
            if (m.equals("")) {
              break;
            }

            // check if the size of the received data (length) equals to size value (size)
            if (size < 0) {
              if (!m.contains("\n\n")) {
                continue;
              }
              String[] headerAndRest = m.split("\n\n", 2);
              String[] headerLines = headerAndRest[0].split("\n");
              if (headerLines.length < 3 || !headerLines[2].startsWith("size:")) {
                continue;
              }
              try {
                size = Integer.parseInt(headerLines[2].substring(5));
              } catch (NumberFormatException ignored) {
                continue;
              }
            }
            if (m.contains("\n\n")) {
              String[] sp_mes = m.split("\n\ndata:", -1);
              d = "";
              for (int i = 1; i < sp_mes.length; i++) {
                d += sp_mes[i] + "\n\n";
              }
              if (d.length() >= 2) {
                if (MySocket.doAdd(m)) d = d.substring(0, d.length() - 2);
              }
              length = d.length();
            }

          } catch (SocketTimeoutException e) {
            continue;
          }
        }
        if (size >= 0 && length > size) {
          MySocket.setExtraMessage(d.substring(size, length)); // optional error
          m = m.substring(0, m.length() - (length - size));
        } else {
          MySocket.setExtraMessage("");
        }

        final String message = m;

        if (!message.equals("")) {
          ClientLogger.log("ReceiveMessagesThread", "Received message bytes=" + message.length());

          activity.runOnUiThread(
              new Runnable() {
                @Override
                public void run() {
                  listener.messageReceived(message, activity);
                }
              });

        } else {
          throw new IOException();
        }
      } catch (IOException e) {
        ClientLogger.logError("ReceiveMessagesThread", "Connection lost while receiving", e);
        MySocket.setSocket(null);
        MySocket.setInput(null);
        MySocket.setOutput(null);
        Intent goSplash = new Intent(activity.getApplicationContext(), SplashActivity.class);
        goSplash.putExtra("LostConnection", true);
        MySocket.setClosed(true);
        activity.startActivity(goSplash);
        break;
      }
    }
    MySocket.setClosed(false);
    MySocket.setExtraMessage("");
  }
}

interface ReceiveMessagesListener {
  /**
   * this function will be called by the Activity when a new message received
   *
   * @param mes the new message
   * @param activity the current activity (this)
   */
  void messageReceived(String mes, Activity activity);
}

class SendMessagesThread implements Runnable {
  private String message;

  public SendMessagesThread(String name, String type, String data) {
    this.message = MySocket.buildMessage(name, type, data);
  }

  public SendMessagesThread(String name, String type) {
    this.message = MySocket.buildMessage(name, type, "");
  }

  @Override
  public void run() {
    ClientLogger.log("SendMessagesThread", "Sending message bytes=" + message.length());
    if (MySocket.getOutput() == null) {
      ClientLogger.logError("SendMessagesThread", "Output stream is null; dropping message", null);
      return;
    }
    MySocket.getOutput().write(message);
    MySocket.getOutput().flush();
  }
}
