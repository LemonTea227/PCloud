package com.example.pcloud;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.Calendar;

public class RegisterActivity extends AppCompatActivity implements ReceiveMessagesListener {
  TextInputLayout usernameRegisterLayout,
      passwordRegisterLayout,
      confirmPasswordRegisterLayout,
      firstNameRegisterLayout,
      lastNameRegisterLayout,
      birthDateRegisterLayout;
  TextInputEditText usernameRegister,
      passwordRegister,
      confirmPasswordRegister,
      firstNameRegister,
      lastNameRegister,
      birthDateRegister;
  Button registerButtonRegister;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_register);

    ReceiveMessagesThread.setActivity(this);
    ReceiveMessagesThread.setListener(RegisterActivity.this);

    new Thread(new ReceiveMessagesThread()).start();

    usernameRegisterLayout = findViewById(R.id.usernameRegisterLayout);
    passwordRegisterLayout = findViewById(R.id.passwordRegisterLayout);
    confirmPasswordRegisterLayout = findViewById(R.id.confirmPasswordRegisterLayout);
    firstNameRegisterLayout = findViewById(R.id.firstNameRegisterLayout);
    lastNameRegisterLayout = findViewById(R.id.lastNameRegisterLayout);
    birthDateRegisterLayout = findViewById(R.id.birthDateRegisterLayout);

    usernameRegister = findViewById(R.id.usernameRegister);
    passwordRegister = findViewById(R.id.passwordRegister);
    confirmPasswordRegister = findViewById(R.id.confirmPasswordRegister);
    firstNameRegister = findViewById(R.id.firstNameRegister);
    lastNameRegister = findViewById(R.id.lastNameRegister);
    birthDateRegister = findViewById(R.id.birthDateRegister);

    final DatePickerDialog[] picker = new DatePickerDialog[1];

    registerButtonRegister = findViewById(R.id.registerButtonRegister);

    if (getIntent().hasExtra("username")) {
      usernameRegister.setText(getIntent().getExtras().getString("username"));
    }

    birthDateRegister.setInputType(InputType.TYPE_NULL);
    birthDateRegister.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            final Calendar cldr = Calendar.getInstance();
            int day = cldr.get(Calendar.DAY_OF_MONTH);
            int month = cldr.get(Calendar.MONTH);
            int year = cldr.get(Calendar.YEAR);
            // date picker dialog
            picker[0] =
                new DatePickerDialog(
                    RegisterActivity.this,
                    new DatePickerDialog.OnDateSetListener() {
                      @Override
                      public void onDateSet(
                          DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                        birthDateRegister.setText(
                            dayOfMonth + "/" + (monthOfYear + 1) + "/" + year);
                      }
                    },
                    year,
                    month,
                    day);
            picker[0].show();
          }
        });

    registerButtonRegister.setOnClickListener(
        v -> {
          try {
            InputMethodManager inputManager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(
                getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);

          } catch (NullPointerException ignored) {
          }

          usernameRegister.setFocusable(false);
          passwordRegister.setFocusable(false);
          confirmPasswordRegister.setFocusable(false);
          firstNameRegister.setFocusable(false);
          lastNameRegister.setFocusable(false);
          birthDateRegister.setFocusable(false);
          usernameRegister.setFocusableInTouchMode(true);
          passwordRegister.setFocusableInTouchMode(true);
          confirmPasswordRegister.setFocusableInTouchMode(true);
          firstNameRegister.setFocusableInTouchMode(true);
          lastNameRegister.setFocusableInTouchMode(true);
          birthDateRegister.setFocusableInTouchMode(true);

          if (!usernameRegister.getText().toString().matches("[a-zA-Z0-9]+")) {
            usernameRegisterLayout.setError(
                getResources().getString(R.string.username_invalid_format));
          } else {
            usernameRegisterLayout.setError(null);
          }
          if (passwordRegister.getText().toString().length() < 8
              || !passwordRegister.getText().toString().matches("[\\u0001-\\u007e]+")) {
            passwordRegisterLayout.setError(
                getResources().getString(R.string.password_invalid_format));
          } else {
            passwordRegisterLayout.setError(null);
          }
          if (confirmPasswordRegister.getText().toString().length() < 8
              || !confirmPasswordRegister.getText().toString().matches("[\\u0001-\\u007e]+")) {
            confirmPasswordRegisterLayout.setError(
                getResources().getString(R.string.password_invalid_format));
          } else {
            confirmPasswordRegisterLayout.setError(null);
          }
          if (!firstNameRegister.getText().toString().matches("[a-zA-Z]+")) {
            firstNameRegisterLayout.setError(
                getResources().getString(R.string.name_invalid_format));
          } else {
            firstNameRegisterLayout.setError(null);
          }
          if (!lastNameRegister.getText().toString().matches("[a-zA-Z]+")) {
            lastNameRegisterLayout.setError(getResources().getString(R.string.name_invalid_format));
          } else {
            lastNameRegisterLayout.setError(null);
          }
          if (!birthDateRegister.getText().toString().matches("[0-9]{1,2}/[0-9]{1,2}/[0-9]{4}")) {
            birthDateRegisterLayout.setError(
                getResources().getString(R.string.birt_date_invalid_format));
          } else {
            birthDateRegisterLayout.setError(null);
          }
          if (usernameRegisterLayout.getError() == null
              && passwordRegisterLayout.getError() == null
              && confirmPasswordRegisterLayout.getError() == null
              && firstNameRegisterLayout.getError() == null
              && lastNameRegisterLayout.getError() == null
              && birthDateRegisterLayout.getError() == null) {
            //                Toast.makeText(getApplicationContext(), "sucsses",
            // Toast.LENGTH_SHORT).show();
            new Thread(
                    new SendMessagesThread(
                        "REGISTER",
                        MessageCodes.getRequest(),
                        String.format(
                            "%s\n%s\n%s\n%s",
                            usernameRegister.getText().toString(),
                            passwordRegister.getText().toString(),
                            firstNameRegister.getText().toString()
                                + " "
                                + lastNameRegister.getText().toString(),
                            birthDateRegister.getText().toString())))
                .start();
          }
        });

    usernameRegister.setOnFocusChangeListener(
        (v, hasFocus) -> {
          usernameRegisterLayout.setError(null);
        });

    passwordRegister.setOnFocusChangeListener(
        (v, hasFocus) -> {
          passwordRegisterLayout.setError(null);
        });

    confirmPasswordRegister.setOnFocusChangeListener(
        (v, hasFocus) -> {
          confirmPasswordRegisterLayout.setError(null);
        });

    firstNameRegister.setOnFocusChangeListener(
        (v, hasFocus) -> {
          firstNameRegisterLayout.setError(null);
        });

    lastNameRegister.setOnFocusChangeListener(
        (v, hasFocus) -> {
          lastNameRegisterLayout.setError(null);
        });

    birthDateRegister.setOnFocusChangeListener(
        (v, hasFocus) -> {
          if (hasFocus) {
            birthDateRegister.callOnClick();
          }
          birthDateRegisterLayout.setError(null);
        });

    confirmPasswordRegister.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {}

          @Override
          public void afterTextChanged(Editable s) {
            if (!confirmPasswordRegister
                .getText()
                .toString()
                .equals(passwordRegister.getText().toString())) {
              confirmPasswordRegisterLayout.setError(
                  getResources().getString(R.string.confirm_password_invalid_format));
            } else {
              confirmPasswordRegisterLayout.setError(null);
            }
          }
        });
  }

  @Override
  public void onBackPressed() {
    if (usernameRegister.getText().toString().equals("")
        && passwordRegister.getText().toString().equals("")
        && confirmPasswordRegister.getText().toString().equals("")
        && firstNameRegister.getText().toString().equals("")
        && lastNameRegister.getText().toString().equals("")
        && birthDateRegister.getText().toString().equals("")) {
      Intent goLogin = new Intent(getApplicationContext(), LoginActivity.class);
      goLogin.putExtra("username", usernameRegister.getText().toString());
      startActivity(goLogin);
    } else {
      usernameRegister.setText("");
      passwordRegister.setText("");
      confirmPasswordRegister.setText("");
      firstNameRegister.setText("");
      lastNameRegister.setText("");
      birthDateRegister.setText("");
      usernameRegisterLayout.setError(null);
      passwordRegisterLayout.setError(null);
      confirmPasswordRegisterLayout.setError(null);
      firstNameRegisterLayout.setError(null);
      lastNameRegisterLayout.setError(null);
      birthDateRegisterLayout.setError(null);
    }
  }

  @Override
  public void messageReceived(String mes, Activity activity) {
    HandelMessage message = new HandelMessage(mes);
    if (message.getName().equals("REGISTER")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        new Thread(new SendMessagesThread("ALBUMS", MessageCodes.getRequest())).start();
      } else if (message.getType().equals(MessageCodes.getRegisterError())) {
        Toast.makeText(
                getApplicationContext(),
                getResources().getString(R.string.register_error),
                Toast.LENGTH_SHORT)
            .show();
      }
    } else if (message.getName().equals("ALBUMS")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        Toast.makeText(
                getApplicationContext(),
                getResources().getString(R.string.getting_in),
                Toast.LENGTH_SHORT)
            .show();
        Intent goMain = new Intent(getApplicationContext(), MainActivity.class);
        goMain.putExtra("albums", message.getData());
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
