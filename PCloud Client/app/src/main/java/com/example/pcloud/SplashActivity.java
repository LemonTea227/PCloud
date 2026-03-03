package com.example.pcloud;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.math.BigInteger;
import java.util.Objects;
import java.util.Random;

public class SplashActivity extends AppCompatActivity implements ReceiveMessagesListener {
  int myNum; // my num for diffie hellman protocol
  BigInteger P =
      new BigInteger(
          "286134470859861285423767856156329902081"); // a prime number diffie hellman protocol
  BigInteger G = null; // the generator of p

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_splash);

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
                //                while(G == null);
                //                BigInteger score = G.pow(myNum).mod(P);
                //                new Thread(new SendMessagesThread("SCORE",
                // MessageCodes.getRequest(), score.toString())).start();
                Intent goLogin = new Intent(getApplicationContext(), LoginActivity.class);
                MySocket.setClosed(true);
                startActivity(goLogin);
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

    if (splashMassage.getName().toUpperCase().equals("SCORE")) {
      BigInteger serverScore = new BigInteger(splashMassage.getData());
      BigInteger aesKey = serverScore.pow(myNum).mod(P);

      byte[] AESKey = bigIntNumToBytes(aesKey);
      MySocket.setAESkey(AESKey);

      //            File sessionFile = new File(getFilesDir(), "session.txt");
      //            if (sessionFile.exists()) {
      //                String session = "";
      //                try {
      //                    FileInputStream sessionFileInput = new FileInputStream(sessionFile);
      //
      //                    byte[] readBuffer = new byte[(int) sessionFile.length()];
      //                    sessionFileInput.read(readBuffer);
      //                    session = new String(readBuffer);
      //                    if (!session.equals("")) {
      //                        new Thread(new SendMessagesThread("SESSION", new String[0],
      // session)).start();
      //                    } else {
      //                        Intent goLogin = new Intent(getApplicationContext(),
      // LoginActivity.class);
      //                        MySocket.setClosed(true);
      //                        startActivity(goLogin);
      //                    }
      //                } catch (FileNotFoundException e) {
      //                    Log.e("login activity", "File not found: " + e.toString());
      //                    Intent goLogin = new Intent(getApplicationContext(),
      // LoginActivity.class);
      //                    MySocket.setClosed(true);
      //                    startActivity(goLogin);
      //                } catch (IOException e) {
      //                    Log.e("login activity", "Can not read file: " + e.toString());
      //                    Intent goLogin = new Intent(getApplicationContext(),
      // LoginActivity.class);
      //                    MySocket.setClosed(true);
      //                    startActivity(goLogin);
      //                }

      //            } else {
      Intent goLogin = new Intent(getApplicationContext(), LoginActivity.class);
      MySocket.setClosed(true);
      startActivity(goLogin);
      //            }
    } else if (splashMassage.getName().toUpperCase().equals("GENERATOR")) {
      G = new BigInteger(splashMassage.getData());
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
