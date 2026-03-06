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

public class RenameAlbumActivity extends AppCompatActivity implements ReceiveMessagesListener {
  private TextInputLayout albumNameRenameLayout;
  private TextInputEditText albumNameRenameEditText;
  private Button renameAlbumButton;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_rename_album);

    ReceiveMessagesThread.setActivity(this);
    ReceiveMessagesThread.setListener(RenameAlbumActivity.this);
    new Thread(new ReceiveMessagesThread()).start();

    Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setDisplayShowHomeEnabled(true);

    albumNameRenameLayout = findViewById(R.id.albumNameRenameLayout);
    albumNameRenameEditText = findViewById(R.id.albumNameRenameEditText);
    renameAlbumButton = findViewById(R.id.renameAlbumButton);

    String oldAlbumName =
        getIntent().hasExtra("old_album_name")
            ? getIntent().getExtras().getString("old_album_name")
            : "";
    albumNameRenameEditText.setText(oldAlbumName);
    albumNameRenameEditText.setSelection(albumNameRenameEditText.getText() == null
        ? 0
        : albumNameRenameEditText.getText().length());

    renameAlbumButton.setOnClickListener(
        v -> {
          try {
            InputMethodManager inputManager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputManager != null && getCurrentFocus() != null) {
              inputManager.hideSoftInputFromWindow(
                  getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            }
          } catch (NullPointerException ignored) {
          }

          String newName =
              albumNameRenameEditText.getText() == null
                  ? ""
                  : albumNameRenameEditText.getText().toString().trim();
          if (!newName.matches("[\\u0001-\\u007e]+")) {
            albumNameRenameLayout.setError(getString(R.string.album_name_invalid_format));
            return;
          }
          if (oldAlbumName.trim().equals(newName)) {
            onBackPressed();
            return;
          }

          albumNameRenameLayout.setError(null);
          new Thread(
                  new SendMessagesThread(
                      "RENAME_ALBUM",
                      MessageCodes.getRequest(),
                      oldAlbumName + "\n" + newName))
              .start();
        });

    albumNameRenameEditText.setOnFocusChangeListener(
        new View.OnFocusChangeListener() {
          @Override
          public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus) {
              albumNameRenameLayout.setError(null);
            }
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
    if (message.getName().equals("RENAME_ALBUM")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        new Thread(new SendMessagesThread("ALBUMS", MessageCodes.getRequest())).start();
      } else if (message.getType().equals(MessageCodes.getRenameAlbumError())) {
        Toast.makeText(getApplicationContext(), getString(R.string.rename_album_error), Toast.LENGTH_SHORT)
            .show();
      }
      return;
    }

    if (message.getName().equals("ALBUMS")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        Intent goMain = new Intent(getApplicationContext(), MainActivity.class);
        goMain.putExtra("albums", message.getData());
        startActivity(goMain);
      } else if (message.getType().equals(MessageCodes.getAlbumsError())) {
        Toast.makeText(getApplicationContext(), getString(R.string.albums_error), Toast.LENGTH_SHORT)
            .show();
      }
    }
  }
}
