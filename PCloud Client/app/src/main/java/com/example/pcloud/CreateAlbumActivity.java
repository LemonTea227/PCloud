package com.example.pcloud;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.Objects;

public class CreateAlbumActivity extends AppCompatActivity implements ReceiveMessagesListener {
  TextInputLayout albumNameCreateAlbumLayout;
  TextInputEditText albumNameCreateAlbum;
  Button createAlbumButton;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_create_album);

    ReceiveMessagesThread.setActivity(this);
    ReceiveMessagesThread.setListener(CreateAlbumActivity.this);

    new Thread(new ReceiveMessagesThread()).start();

    Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setDisplayShowHomeEnabled(true);

    albumNameCreateAlbumLayout = findViewById(R.id.albumNameCreateAlbumLayout);
    albumNameCreateAlbum = findViewById(R.id.albumNameCreateAlbum);
    createAlbumButton = findViewById(R.id.createAlbumButton);

    createAlbumButton.setOnClickListener(
        v -> {
          try {
            InputMethodManager inputManager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(
                getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);

          } catch (NullPointerException ignored) {
          }

          albumNameCreateAlbum.setFocusable(false);
          albumNameCreateAlbum.setFocusableInTouchMode(true);

          if (!albumNameCreateAlbum.getText().toString().matches("[\\u0001-\\u007e]+")) {
            albumNameCreateAlbumLayout.setError(
                getResources().getString(R.string.album_name_invalid_format));
          } else {
            albumNameCreateAlbumLayout.setError(null);
          }

          if (albumNameCreateAlbumLayout.getError() == null) {
            //                Toast.makeText(getApplicationContext(), "sucsses",
            // Toast.LENGTH_SHORT).show();
            new Thread(
                    new SendMessagesThread(
                        "NEW_ALBUM",
                        MessageCodes.getRequest(),
                        albumNameCreateAlbum.getText().toString()))
                .start();
          }
        });
    albumNameCreateAlbum.setOnFocusChangeListener(
        new View.OnFocusChangeListener() {
          @Override
          public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus) albumNameCreateAlbumLayout.setError(null);
          }
        });
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      onBackPressed();
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onBackPressed() {
    Intent goMain = new Intent(getApplicationContext(), MainActivity.class);
    if (getIntent().hasExtra("albums")) {
      goMain.putExtra("albums", getIntent().getExtras().getString("albums"));
    }
    startActivity(goMain);
  }

  @Override
  public void messageReceived(String mes, Activity activity) {
    HandelMessage message = new HandelMessage(mes);
    if (message.getName().equals("NEW_ALBUM")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        new Thread(new SendMessagesThread("ALBUMS", MessageCodes.getRequest())).start();
      } else if (message.getType().equals(MessageCodes.getNewAlbumError())) {
        Toast.makeText(
                getApplicationContext(),
                getResources().getString(R.string.new_album_error),
                Toast.LENGTH_SHORT)
            .show();
      }
    } else if (message.getName().equals("ALBUMS")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
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
