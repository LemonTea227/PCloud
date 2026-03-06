package com.example.pcloud;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class AddPhotoActivity extends AppCompatActivity implements ReceiveMessagesListener {
  private static final int RESULT_LOAD_IMAGE = 1;
  Boolean imageSelected = false;
  String mimeType = "";
  String nameOfFile = "";
  android.net.Uri selectedImageUri = null;
  private final Set<Integer> uploadAckedParts = new LinkedHashSet<>();
  private int uploadExpectedParts = 0;
  private String uploadTrackingFileName = "";

  ImageButton choosePhotoAddPhotoImageButton;
  Button addPhotoButton;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_add_photo);
    java.text.DateFormat dateFormat =
        android.text.format.DateFormat.getDateFormat(getApplicationContext());

    ReceiveMessagesThread.setActivity(this);
    ReceiveMessagesThread.setListener(AddPhotoActivity.this);

    new Thread(new ReceiveMessagesThread()).start();

    Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setDisplayShowHomeEnabled(true);

    if (getIntent().hasExtra("photos")) {
      setTitle(getIntent().getExtras().getString("photos").split("\n")[0]);
    }

    choosePhotoAddPhotoImageButton = findViewById(R.id.choosePhotoAddPhotoImageButton);
    addPhotoButton = findViewById(R.id.addPhotoButton);

    addPhotoButton.setOnClickListener(
        v -> {
          try {
            InputMethodManager inputManager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(
                getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);

          } catch (NullPointerException ignored) {
          }

          if (imageSelected) {
            byte[] rawImageBytes = readBytesFromUri(selectedImageUri);
            if (rawImageBytes == null || rawImageBytes.length == 0) {
              return;
            }
            nameOfFile = ensureFileNameHasExtension(nameOfFile, selectedImageUri, rawImageBytes);
            String encodedImage = Base64.encodeToString(rawImageBytes, Base64.NO_WRAP);
            MySocket.beginTransfer();
            TransferNotificationHelper.showUploadProgress(getApplicationContext(), 0, 0);
            if (getIntent().hasExtra("photos")) {
              String albumName = getIntent().getExtras().getString("photos").split("\n")[0];
              sendUploadInChunks(albumName, nameOfFile, encodedImage);
            } else {
              SendMessagesThread.queueMessage(
                  "UPLOAD_PHOTO", MessageCodes.getRequest(), nameOfFile + "\n" + encodedImage);
            }

          } else {
            Intent goGallery =
                new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(goGallery, RESULT_LOAD_IMAGE);
          }
        });

    choosePhotoAddPhotoImageButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            Intent goGallery =
                new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(goGallery, RESULT_LOAD_IMAGE);
          }
        });
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    ReceiveMessagesThread.setActivity(this);
    ReceiveMessagesThread.setListener(AddPhotoActivity.this);
    if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && data != null) {
      selectedImageUri = data.getData();
      nameOfFile = getFileName(selectedImageUri);
      String[] mimeTypeSplit = getContentResolver().getType(selectedImageUri).split("/");
      mimeType = mimeTypeSplit[mimeTypeSplit.length - 1];
      choosePhotoAddPhotoImageButton.setImageURI(selectedImageUri);
      imageSelected = true;
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      Intent goSecond = new Intent(getApplicationContext(), SecondActivity.class);
      if (getIntent().hasExtra("albums")) {
        goSecond.putExtra("albums", getIntent().getExtras().getString("albums"));
      }

      if (getIntent().hasExtra("photos")) {
        goSecond.putExtra("photos", getIntent().getExtras().getString("photos"));
      }

      startActivity(goSecond);
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onBackPressed() {
    if (imageSelected) {
      choosePhotoAddPhotoImageButton.setImageResource(R.drawable.photo_icon);
      imageSelected = false;
    } else {
      Intent goSecond = new Intent(getApplicationContext(), SecondActivity.class);
      if (getIntent().hasExtra("albums")) {
        goSecond.putExtra("albums", getIntent().getExtras().getString("albums"));
      }

      if (getIntent().hasExtra("photos")) {
        goSecond.putExtra("photos", getIntent().getExtras().getString("photos"));
      }

      startActivity(goSecond);
    }
  }

  @Override
  public void messageReceived(String mes, Activity activity) {
    HandelMessage message = new HandelMessage(mes);
    if (message.getName().equals("UPLOAD_PHOTO")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        MySocket.endTransfer();
        TransferNotificationHelper.completeUpload(getApplicationContext());
        resetUploadProgressTracking();
      } else if (message.getType().equals(MessageCodes.getUploadPhotoError())) {
        MySocket.endTransfer();
        TransferNotificationHelper.failUpload(getApplicationContext());
        resetUploadProgressTracking();
      }
    }

    if (message.getName().equals("UPLOAD_PHOTO_CHUNK")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        String[] lines = message.getData().split("\\n");
        if (lines.length >= 3) {
          String ackFileName = lines[1];
          if (!ackFileName.equals(uploadTrackingFileName)) {
            return;
          }
          try {
            int ackPartIndex = Integer.parseInt(lines[2]);
            uploadAckedParts.add(ackPartIndex);
            if (uploadExpectedParts > 0) {
              TransferNotificationHelper.showUploadProgress(
                  getApplicationContext(), uploadAckedParts.size(), uploadExpectedParts);
            }
          } catch (NumberFormatException ignored) {
          }
        }
      }
    }
  }

  private void resetUploadProgressTracking() {
    uploadAckedParts.clear();
    uploadExpectedParts = 0;
    uploadTrackingFileName = "";
  }

  public String getFileName(android.net.Uri uri) {
    String result = null;
    if (uri.getScheme().equals("content")) {
      Cursor cursor = getContentResolver().query(uri, null, null, null, null);
      try {
        if (cursor != null && cursor.moveToFirst()) {
          int displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
          if (displayNameIndex >= 0) {
            result = cursor.getString(displayNameIndex);
          }
        }
      } finally {
        if (cursor != null) {
          cursor.close();
        }
      }
    }
    if (result == null) {
      result = uri.getPath();
      int cut = result.lastIndexOf('/');
      if (cut != -1) {
        result = result.substring(cut + 1);
      }
    }
    return result;
  }

  private byte[] readBytesFromUri(android.net.Uri uri) {
    if (uri == null) {
      return null;
    }
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try {
      InputStream inputStream = getContentResolver().openInputStream(uri);
      if (inputStream == null) {
        return null;
      }
      try {
        byte[] buffer = new byte[16384];
        int count;
        while ((count = inputStream.read(buffer)) != -1) {
          outputStream.write(buffer, 0, count);
        }
      } finally {
        inputStream.close();
      }
      return outputStream.toByteArray();
    } catch (IOException e) {
      return null;
    }
  }

  private void sendUploadInChunks(String albumName, String fileName, String encodedImage) {
    if (albumName == null || fileName == null || encodedImage == null) {
      return;
    }
    new Thread(
            () -> {
              int chunkSize = 7000;
              int totalParts = (encodedImage.length() + chunkSize - 1) / chunkSize;
              if (totalParts <= 0) {
                MySocket.endTransfer();
                TransferNotificationHelper.failUpload(getApplicationContext());
                return;
              }

              String startPayload = albumName + "\n" + fileName + "\n" + totalParts;
                uploadExpectedParts = totalParts;
                uploadTrackingFileName = fileName;
                uploadAckedParts.clear();
              SendMessagesThread.queueMessage(
                  "UPLOAD_PHOTO_START", MessageCodes.getRequest(), startPayload);
              TransferNotificationHelper.showUploadProgress(getApplicationContext(), 0, totalParts);

              for (int partIndex = 0; partIndex < totalParts; partIndex++) {
                int start = partIndex * chunkSize;
                int end = Math.min(encodedImage.length(), start + chunkSize);
                String chunk = encodedImage.substring(start, end);
                String chunkPayload =
                    albumName
                        + "\n"
                        + fileName
                        + "\n"
                        + partIndex
                        + "\n"
                        + totalParts
                        + "\n"
                        + chunk;
                SendMessagesThread.queueMessage(
                    "UPLOAD_PHOTO_CHUNK", MessageCodes.getRequest(), chunkPayload);
              }
            })
        .start();
  }

  private String ensureFileNameHasExtension(
      String fileName, android.net.Uri uri, byte[] rawBytes) {
    String safeName = fileName == null ? "" : fileName.trim();
    if (safeName.equals("")) {
      safeName = "media_" + System.currentTimeMillis();
    }
    int dotIndex = safeName.lastIndexOf('.');
    if (dotIndex > 0 && dotIndex < safeName.length() - 1) {
      return safeName;
    }

    String contentType = getContentResolver().getType(uri);
    String extension =
        contentType == null
            ? null
            : MimeTypeMap.getSingleton().getExtensionFromMimeType(contentType);

    if ((extension == null || extension.trim().equals("")) && rawBytes != null && rawBytes.length >= 6) {
      if (rawBytes[0] == 'G'
          && rawBytes[1] == 'I'
          && rawBytes[2] == 'F'
          && rawBytes[3] == '8'
          && (rawBytes[4] == '7' || rawBytes[4] == '9')
          && rawBytes[5] == 'a') {
        extension = "gif";
      }
    }

    if (extension == null || extension.trim().equals("")) {
      return safeName;
    }
    return safeName + "." + extension.toLowerCase(Locale.ROOT);
  }
}
