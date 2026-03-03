package com.example.pcloud;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class LoginActivity extends AppCompatActivity implements ReceiveMessagesListener {
  TextInputLayout usernameLoginLayout, passwordLoginLayout;
  TextInputEditText usernameLogin, passwordLogin;
  Button loginButtonLogin, registerButtonLogin;
  CheckBox rememberMeCheckBox;
  private boolean pendingRememberChoice;
  private String pendingUsername;
  private String pendingPassword;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_login);
    ClientLogger.init(getApplicationContext());

    ReceiveMessagesThread.setActivity(this);
    ReceiveMessagesThread.setListener(LoginActivity.this);

    new Thread(new ReceiveMessagesThread()).start();

    usernameLoginLayout = findViewById(R.id.usernameLoginLayout);
    passwordLoginLayout = findViewById(R.id.passwordLoginLayout);
    usernameLogin = findViewById(R.id.usernameLogin);
    passwordLogin = findViewById(R.id.passwordLogin);
    loginButtonLogin = findViewById(R.id.loginButtonLogin);
    registerButtonLogin = findViewById(R.id.registerButtonLogin);
    rememberMeCheckBox = findViewById(R.id.rememberMeLoginCheckBox);

    rememberMeCheckBox.setChecked(SessionPrefs.shouldKeepLoggedIn(this));

    if (getIntent().hasExtra("username")) {
      usernameLogin.setText(getIntent().getExtras().getString("username"));
    }

    loginButtonLogin.setOnClickListener(
        v -> {
          try {
            InputMethodManager inputManager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(
                getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);

          } catch (NullPointerException ignored) {
          }

          usernameLogin.setFocusable(false);
          passwordLogin.setFocusable(false);
          usernameLogin.setFocusableInTouchMode(true);
          passwordLogin.setFocusableInTouchMode(true);

          if (!usernameLogin.getText().toString().matches("[a-zA-Z0-9]+")) {
            usernameLoginLayout.setError(
                getResources().getString(R.string.username_invalid_format));
          } else {
            usernameLoginLayout.setError(null);
          }
          if (passwordLogin.getText().toString().length() < 8
              || !passwordLogin.getText().toString().matches("[\\u0001-\\u007e]+")) {
            passwordLoginLayout.setError(
                getResources().getString(R.string.password_invalid_format));
          } else {
            passwordLoginLayout.setError(null);
          }

          if (usernameLoginLayout.getError() == null && passwordLoginLayout.getError() == null) {
            pendingUsername = usernameLogin.getText().toString();
            pendingPassword = passwordLogin.getText().toString();
            pendingRememberChoice = rememberMeCheckBox.isChecked();
            ClientLogger.log("LoginActivity", "Sending LOGIN request for user=" + pendingUsername);
            //                Toast.makeText(getApplicationContext(), "sucsses",
            // Toast.LENGTH_SHORT).show();
            new Thread(
                    new SendMessagesThread(
                        "LOGIN",
                        MessageCodes.getRequest(),
                        String.format(
                            "%s\n%s",
                            usernameLogin.getText().toString(),
                            passwordLogin.getText().toString())))
                .start();
          }
        });
    usernameLogin.setOnFocusChangeListener(
        new View.OnFocusChangeListener() {
          @Override
          public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus) usernameLoginLayout.setError(null);
          }
        });

    passwordLogin.setOnFocusChangeListener(
        new View.OnFocusChangeListener() {
          @Override
          public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus) passwordLoginLayout.setError(null);
          }
        });

    registerButtonLogin.setOnClickListener(
        v -> {
          Intent goRegister = new Intent(getApplicationContext(), RegisterActivity.class);
          goRegister.putExtra("username", usernameLogin.getText().toString());
          MySocket.setClosed(true);
          startActivity(goRegister);
        });
  }

  @Override
  public void onBackPressed() {
    if (!(usernameLogin.getText().toString().equals("")
        && passwordLogin.getText().toString().equals(""))) {
      usernameLogin.setText("");
      passwordLogin.setText("");
      usernameLoginLayout.setError(null);
      passwordLoginLayout.setError(null);
    }
  }

  @Override
  public void messageReceived(String mes, Activity activity) {
    //        Toast.makeText(getApplicationContext(), mes, Toast.LENGTH_SHORT).show();
    HandelMessage message = new HandelMessage(mes);
    if (message.getName().equals("LOGIN")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        SessionPrefs.setKeepLoggedIn(this, pendingRememberChoice);
        if (pendingRememberChoice) {
          SessionPrefs.saveCredentials(this, pendingUsername, pendingPassword);
        } else {
          SessionPrefs.clearCredentials(this);
        }
        ClientLogger.log("LoginActivity", "LOGIN confirmed");
        new Thread(new SendMessagesThread("ALBUMS", MessageCodes.getRequest())).start();
      } else if (message.getType().equals(MessageCodes.getLoginError())) {
        ClientLogger.log("LoginActivity", "LOGIN failed with server error");
        Toast.makeText(
                getApplicationContext(),
                getResources().getString(R.string.login_error),
                Toast.LENGTH_SHORT)
            .show();
      }
    } else if (message.getName().equals("ALBUMS")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        ClientLogger.log("LoginActivity", "ALBUMS response received, opening MainActivity");
        Toast.makeText(
                getApplicationContext(),
                getResources().getString(R.string.getting_in),
                Toast.LENGTH_SHORT)
            .show();
        Intent goMain = new Intent(getApplicationContext(), MainActivity.class);
        goMain.putExtra("albums", message.getData());
        MySocket.setClosed(true);
        startActivity(goMain);
      } else if (message.getType().equals(MessageCodes.getAlbumsError())) {
        Toast.makeText(
                getApplicationContext(),
                getResources().getString(R.string.albums_error),
                Toast.LENGTH_SHORT)
            .show();
      }
    }
  }
}
