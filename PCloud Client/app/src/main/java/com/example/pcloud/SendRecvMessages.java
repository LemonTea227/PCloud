package com.example.pcloud;

import android.app.Activity;
import android.content.Intent;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

class ReceiveMessagesThread implements Runnable {
  private static Activity activity;
  private static ReceiveMessagesListener listener;
  private static final AtomicBoolean running = new AtomicBoolean(false);

  public static synchronized void setListener(ReceiveMessagesListener listener) {
    ReceiveMessagesThread.listener = listener;
  }

  public static synchronized void setActivity(Activity activity) {
    ReceiveMessagesThread.activity = activity;
  }

  @Override
  public void run() {
    if (!running.compareAndSet(false, true)) {
      ClientLogger.log(
          "ReceiveMessagesThread", "Receiver loop already running; skipping duplicate start");
      return;
    }

    if (activity == null) {
      running.set(false);
      return;
    }
    ClientLogger.log(
        "ReceiveMessagesThread",
        "Receiver loop started for " + activity.getClass().getSimpleName());
    try {
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
          if (m == null) {
            m = "";
          }
          char[] buffer = new char[1024];

          while (true) {
            try {
              int headerEndIndex = m.indexOf("\n\n");
              if (headerEndIndex < 0) {
                int charsRead = input.read(buffer);
                if (charsRead == -1) {
                  continue;
                }
                m += new String(buffer, 0, charsRead);
                continue;
              }

              String header = m.substring(0, headerEndIndex);
              String[] headerLines = header.split("\n");
              if (headerLines.length < 3 || !headerLines[2].startsWith("size:")) {
                int charsRead = input.read(buffer);
                if (charsRead == -1) {
                  continue;
                }
                m += new String(buffer, 0, charsRead);
                continue;
              }

              int size;
              try {
                size = Integer.parseInt(headerLines[2].substring(5));
              } catch (NumberFormatException ignored) {
                int charsRead = input.read(buffer);
                if (charsRead == -1) {
                  continue;
                }
                m += new String(buffer, 0, charsRead);
                continue;
              }

              int dataStartIndex = headerEndIndex + 7;
              int messageLength = dataStartIndex + size;
              if (m.length() < messageLength) {
                int charsRead = input.read(buffer);
                if (charsRead == -1) {
                  continue;
                }
                m += new String(buffer, 0, charsRead);
                continue;
              }

              final String message = m.substring(0, messageLength);
              if (m.length() > messageLength) {
                MySocket.setExtraMessage(m.substring(messageLength));
              } else {
                MySocket.setExtraMessage("");
              }

              if (!message.equals("")) {
                ClientLogger.log(
                    "ReceiveMessagesThread", "Received message bytes=" + message.length());

                if (activity != null && listener != null) {
                  activity.runOnUiThread(
                      new Runnable() {
                        @Override
                        public void run() {
                          listener.messageReceived(message, activity);
                        }
                      });
                }

                break;
              }

              throw new IOException();

            } catch (SocketTimeoutException e) {
              continue;
            }
          }
        } catch (IOException e) {
          if (MySocket.isClosed()) {
            break;
          }
          ClientLogger.logError("ReceiveMessagesThread", "Connection lost while receiving", e);
          MySocket.setSocket(null);
          MySocket.setInput(null);
          MySocket.setOutput(null);
          if (activity != null) {
            Intent goSplash = new Intent(activity.getApplicationContext(), SplashActivity.class);
            goSplash.putExtra("LostConnection", true);
            MySocket.setClosed(true);
            activity.startActivity(goSplash);
          }
          break;
        }
      }
    } finally {
      running.set(false);
      MySocket.setExtraMessage("");
    }
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
