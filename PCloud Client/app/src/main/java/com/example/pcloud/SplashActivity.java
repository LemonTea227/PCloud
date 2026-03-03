package com.example.pcloud;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.math.BigInteger;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

public class SplashActivity extends AppCompatActivity implements ReceiveMessagesListener {
  int myNum; // my num for diffie hellman protocol
  BigInteger P =
      new BigInteger(
          "286134470859861285423767856156329902081"); // a prime number diffie hellman protocol
  BigInteger G = null; // the generator of p
  private boolean scoreSent = false;
  private boolean handshakeComplete = false;
  private boolean autoLoginAttempt = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_splash);
    ClientLogger.init(getApplicationContext());
    ClientLogger.installCrashHandler(getApplicationContext());
    ClientLogger.log("SplashActivity", "Splash started and crash logger initialized");

    if (getIntent().hasExtra("LostConnection")) {
      if (Objects.requireNonNull(getIntent().getExtras()).getBoolean("LostConnection")) {
        Toast.makeText(this, getResources().getString(R.string.lost_connection), Toast.LENGTH_SHORT)
            .show();
      }
    }

    setTitle("");

    ReceiveMessagesThread.setActivity(this);
    ReceiveMessagesThread.setListener(SplashActivity.this);

    new Thread(new ConnectionThread()).start();
    myNum = new Random().nextInt(20302) + 1;

    new Thread(
            new Runnable() {
              @Override
              public void run() {
                while (MySocket.getInput() == null)
                  ;
                new Thread(new ReceiveMessagesThread()).start();
                ClientLogger.log(
                    "SplashActivity", "Socket connected, waiting for handshake messages");
              }
            })
        .start();
  }

  /**
   * converting BigInteger num to bytes Array of AES key
   *
   * @param num the num we want to convert
   * @return bytes Array of AES key
   */
  private byte[] bigIntNumToBytes(BigInteger num) {
    byte[] bytes = new byte[16];
    for (int i = bytes.length - 1; i >= 0; i--) {
      byte newNum = (byte) num.mod(new BigInteger("256")).intValue();
      num = num.divide(new BigInteger("256"));
      bytes[i] = newNum;
    }
    return bytes;
  }

  @Override
  public void messageReceived(String mes, Activity activity) {
    HandelMessage splashMassage = new HandelMessage(mes);

    if (splashMassage.getName().toUpperCase(Locale.ROOT).equals("SCORE")) {
      BigInteger serverScore = new BigInteger(splashMassage.getData());
      BigInteger aesKey = serverScore.pow(myNum).mod(P);

      byte[] AESKey = bigIntNumToBytes(aesKey);
      MySocket.setAESkey(AESKey);
      handshakeComplete = true;
      ClientLogger.log("SplashActivity", "Handshake completed and AES key set");

      if (SessionPrefs.shouldKeepLoggedIn(this)) {
        String savedUsername = SessionPrefs.getSavedUsername(this);
        String savedPassword = SessionPrefs.getSavedPassword(this);
        if (!savedUsername.isEmpty() && !savedPassword.isEmpty()) {
          autoLoginAttempt = true;
          ClientLogger.log("SplashActivity", "Attempting auto login for saved user");
          new Thread(
                  new SendMessagesThread(
                      "LOGIN", MessageCodes.getRequest(), savedUsername + "\n" + savedPassword))
              .start();
          return;
        }
      }

      Intent goLogin = new Intent(getApplicationContext(), LoginActivity.class);
      MySocket.setClosed(true);
      startActivity(goLogin);
    } else if (splashMassage.getName().toUpperCase(Locale.ROOT).equals("GENERATOR")) {
      G = new BigInteger(splashMassage.getData());
      if (!scoreSent) {
        BigInteger score = G.pow(myNum).mod(P);
        ClientLogger.log("SplashActivity", "Received generator, sending SCORE");
        new Thread(new SendMessagesThread("SCORE", MessageCodes.getRequest(), score.toString()))
            .start();
        scoreSent = true;
      }
    } else if (splashMassage.getName().toUpperCase(Locale.ROOT).equals("LOGIN")
        && autoLoginAttempt
        && handshakeComplete) {
      if (splashMassage.getType().equals(MessageCodes.getConfirm())) {
        new Thread(new SendMessagesThread("ALBUMS", MessageCodes.getRequest())).start();
      } else {
        ClientLogger.log("SplashActivity", "Auto login failed, opening login page");
        Intent goLogin = new Intent(getApplicationContext(), LoginActivity.class);
        MySocket.setClosed(true);
        startActivity(goLogin);
      }
    } else if (splashMassage.getName().toUpperCase(Locale.ROOT).equals("ALBUMS")
        && autoLoginAttempt
        && handshakeComplete) {
      if (splashMassage.getType().equals(MessageCodes.getConfirm())) {
        Intent goMain = new Intent(getApplicationContext(), MainActivity.class);
        goMain.putExtra("albums", splashMassage.getData());
        MySocket.setClosed(true);
        startActivity(goMain);
      }
    }
    //        } else if (splashMassage.getName().toUpperCase().equals("SESSION")) {
    //            if (splashMassage.getType().equals(MessageCodes.getConfirm())) {
    //                new Thread(new SendMessagesThread("HOME", new String[0], "")).start();
    //            } else if (splashMassage.getType().equals(MessageCodes.getSessionError())) {
    //                Intent goLogin = new Intent(getApplicationContext(), LoginActivity.class);
    //                MySocket.setClosed(true);
    //                startActivity(goLogin);
    //            }
    //        } else if (splashMassage.getType().equals("HOME")) {
    //            MySocket.setFullName(splashMassage.getHeader("full_name"));
    //            Intent homePage = new Intent(getApplicationContext(), MainPageActivity.class);
    //            homePage.putExtra("HOME", splashMassage.getData());
    //            MySocket.setClosed(true);
    //            startActivity(homePage);
    //        }
  }
}
