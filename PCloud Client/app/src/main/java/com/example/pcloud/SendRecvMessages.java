package com.example.pcloud;

import android.app.Activity;
import android.content.Intent;
import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigInteger;
import java.net.SocketTimeoutException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

class ReceiveMessagesThread implements Runnable {
  private static final BigInteger DH_P = new BigInteger("286134470859861285423767856156329902081");
  private static Activity activity;
  private static ReceiveMessagesListener listener;
  private static final AtomicBoolean running = new AtomicBoolean(false);
  private boolean reconnectingHandshake = false;
  private int reconnectSecret = 0;

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

                if (reconnectingHandshake && handleReconnectHandshakeMessage(message)) {
                  break;
                }

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
          if (tryReconnectInPlace()) {
            continue;
          }
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

  private byte[] bigIntNumToBytes(BigInteger num) {
    byte[] bytes = new byte[16];
    for (int i = bytes.length - 1; i >= 0; i--) {
      byte newNum = (byte) num.mod(new BigInteger("256")).intValue();
      num = num.divide(new BigInteger("256"));
      bytes[i] = newNum;
    }
    return bytes;
  }

  private boolean tryReconnectInPlace() {
    try {
      reconnectSecret = new Random().nextInt(20302) + 1;
      MySocket.setAESkey(new byte[] {});
      reconnectingHandshake = true;
      new ConnectionThread().run();
      boolean connected = MySocket.getInput() != null && MySocket.getOutput() != null;
      if (!connected) {
        reconnectingHandshake = false;
      }
      return connected;
    } catch (Exception ex) {
      reconnectingHandshake = false;
      return false;
    }
  }

  private boolean handleReconnectHandshakeMessage(String message) {
    HandelMessage handshakeMessage = new HandelMessage(message);
    String name = handshakeMessage.getName();
    if (name == null) {
      return true;
    }
    String upperName = name.toUpperCase();
    if (upperName.equals("GENERATOR")) {
      try {
        BigInteger g = new BigInteger(handshakeMessage.getData());
        BigInteger score = g.pow(reconnectSecret).mod(DH_P);
        new Thread(new SendMessagesThread("SCORE", MessageCodes.getRequest(), score.toString()))
            .start();
      } catch (Exception ignored) {
      }
      return true;
    }
    if (upperName.equals("SCORE")) {
      try {
        BigInteger serverScore = new BigInteger(handshakeMessage.getData());
        BigInteger aesKey = serverScore.pow(reconnectSecret).mod(DH_P);
        MySocket.setAESkey(bigIntNumToBytes(aesKey));
      } catch (Exception ignored) {
      }
      reconnectingHandshake = false;
      return true;
    }
    return true;
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
  private static final ExecutorService sendExecutor = Executors.newSingleThreadExecutor();
  private static final Object sendLock = new Object();
  private String message;

  public static void queueMessage(String name, String type, String data) {
    String outbound = MySocket.buildMessage(name, type, data);
    queueRawMessage(outbound);
  }

  public static void queueMessage(String name, String type) {
    queueMessage(name, type, "");
  }

  private static void queueRawMessage(String outbound) {
    sendExecutor.execute(
        () -> {
          ClientLogger.log("SendMessagesThread", "Sending message bytes=" + outbound.length());
          if (MySocket.getOutput() == null) {
            ClientLogger.logError(
                "SendMessagesThread", "Output stream is null; dropping message", null);
            return;
          }
          synchronized (sendLock) {
            MySocket.getOutput().write(outbound);
            MySocket.getOutput().flush();
          }
        });
  }

  public SendMessagesThread(String name, String type, String data) {
    this.message = MySocket.buildMessage(name, type, data);
  }

  public SendMessagesThread(String name, String type) {
    this.message = MySocket.buildMessage(name, type, "");
  }

  @Override
  public void run() {
    queueRawMessage(message);
  }
}
