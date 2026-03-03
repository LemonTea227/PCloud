package com.example.pcloud;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Objects;

public class SecondActivity extends AppCompatActivity implements ReceiveMessagesListener {
  private static final int PREVIEW_MAX_DIMENSION = 320;
  private RecyclerView photosSecondRecyclerView;
  private RecyclerView.Adapter adapter;
  private RecyclerView.LayoutManager layoutManager;
  private static final int RESULT_LOAD_IMAGE = 1;
  private String mimeType;
  private String nameOfFile;

  private ArrayList<PhotosItem> photos;

  private boolean first;

  @RequiresApi(api = Build.VERSION_CODES.O)
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_second);
    java.text.DateFormat dateFormat =
        android.text.format.DateFormat.getDateFormat(getApplicationContext());

    ReceiveMessagesThread.setActivity(this);
    ReceiveMessagesThread.setListener(SecondActivity.this);

    new Thread(new ReceiveMessagesThread()).start();

    Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setDisplayShowHomeEnabled(true);

    photos = new ArrayList<PhotosItem>();
    mimeType = "";
    nameOfFile = "";
    first = true;

    if (getIntent().hasExtra("album_name")) {
      setTitle(getIntent().getExtras().getString("album_name"));
      new Thread(
              new SendMessagesThread(
                  "PHOTOS",
                  MessageCodes.getRequest(),
                  getIntent().getExtras().getString("album_name")))
          .start();
    }

    if (getIntent().hasExtra("album_name") && getIntent().hasExtra("albums")) {
      adapter =
          new PhotosAdapter(
              getApplicationContext(),
              this.photos,
              getIntent().getExtras().getString("albums"),
              getIntent().getExtras().getString("album_name"));
    } else {
      adapter = new PhotosAdapter(getApplicationContext(), this.photos);
    }

    photosSecondRecyclerView = findViewById(R.id.photosSecondRecyclerView);

    photosSecondRecyclerView.setHasFixedSize(true);
    layoutManager = new LinearLayoutManager(this);

    photosSecondRecyclerView.setLayoutManager(layoutManager);
    photosSecondRecyclerView.setAdapter(adapter);
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  private void addPhoto(String message) {
    String[] parts = message.split("~", 2);
    if (parts.length < 2) {
      return;
    }
    String photoName = parts[0];
    Bitmap decodedPhoto = decodePreviewBitmap(parts[1]);
    if (decodedPhoto != null) {
      appendPhotoToRows(photoName, decodedPhoto);
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  private void generatePhotos(String message) {
    if (message != null && !message.equals("")) {
      String[] photosMessage = message.split("\n");
      for (int i = 1; i < photosMessage.length; i++) {
        String[] parts = photosMessage[i].split("~", 2);
        if (parts.length < 2) {
          continue;
        }
        Bitmap decodedPreview = decodePreviewBitmap(parts[1]);
        if (decodedPreview != null) {
          appendPhotoToRows(parts[0], decodedPreview);
        }
      }
    }
  }

  private void appendPhotoToRows(String photoName, Bitmap decodedPhoto) {
    if (photos.size() != 0) {
      PhotosItem lastRow = photos.get(photos.size() - 1);
      if (lastRow.getSecondPhotoIcon() == null) {
        lastRow.setSecondName(photoName);
        lastRow.setSecondPhotoIcon(decodedPhoto);
        adapter.notifyItemChanged(photos.size() - 1);
      } else if (lastRow.getThirdPhotoIcon() == null) {
        lastRow.setThirdName(photoName);
        lastRow.setThirdPhotoIcon(decodedPhoto);
        adapter.notifyItemChanged(photos.size() - 1);
      } else if (lastRow.getFourthPhotoIcon() == null) {
        lastRow.setFourtName(photoName);
        lastRow.setFourthPhotoIcon(decodedPhoto);
        adapter.notifyItemChanged(photos.size() - 1);
      } else {
        photos.add(new PhotosItem(photoName, decodedPhoto));
        adapter.notifyItemInserted(photos.size() - 1);
      }
    } else {
      photos.add(new PhotosItem(photoName, decodedPhoto));
      adapter.notifyItemInserted(photos.size() - 1);
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  private Bitmap decodePreviewBitmap(String photoMessage) {
    try {
      byte[] decodedString = Base64.getDecoder().decode(photoMessage);
      return decodeSampledBitmapFromBytes(
          decodedString, PREVIEW_MAX_DIMENSION, PREVIEW_MAX_DIMENSION);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private Bitmap decodeSampledBitmapFromBytes(byte[] imageData, int reqWidth, int reqHeight) {
    BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
    boundsOptions.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(imageData, 0, imageData.length, boundsOptions);

    BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
    decodeOptions.inSampleSize = calculateInSampleSize(boundsOptions, reqWidth, reqHeight);
    decodeOptions.inPreferredConfig = Bitmap.Config.RGB_565;
    return BitmapFactory.decodeByteArray(imageData, 0, imageData.length, decodeOptions);
  }

  private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
    int height = options.outHeight;
    int width = options.outWidth;
    int inSampleSize = 1;

    if (height > reqHeight || width > reqWidth) {
      int halfHeight = height / 2;
      int halfWidth = width / 2;

      while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
        inSampleSize *= 2;
      }
    }

    return Math.max(1, inSampleSize);
  }

  public boolean onPrepareOptionsMenu(Menu menu) {
    if (first) {
      MenuInflater inflater = getMenuInflater();
      inflater.inflate(R.menu.photos_menu, menu);
      first = false;
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      onBackPressed();
    }

    switch (item.getItemId()) {
      case R.id.addPhotosMenuItem:
        Intent goGallery =
            new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        MySocket.setClosed(true);
        startActivityForResult(goGallery, RESULT_LOAD_IMAGE);
        return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    ReceiveMessagesThread.setActivity(this);
    ReceiveMessagesThread.setListener(SecondActivity.this);
    new Thread(new ReceiveMessagesThread()).start();
    if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && data != null) {
      android.net.Uri selectedImage = data.getData();
      if (selectedImage == null) {
        return;
      }
      nameOfFile = getFileName(selectedImage);
      String contentType = getContentResolver().getType(selectedImage);
      if (contentType == null) {
        contentType = "image/png";
      }
      String[] mimeTypeSplit = contentType.split("/");
      mimeType = mimeTypeSplit[mimeTypeSplit.length - 1];
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      Bitmap imageToUpload = null;
      try {
        InputStream stream = getContentResolver().openInputStream(selectedImage);
        if (stream != null) {
          try {
            imageToUpload = BitmapFactory.decodeStream(stream);
          } finally {
            stream.close();
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
      if (imageToUpload == null) {
        Toast.makeText(
                getApplicationContext(), getString(R.string.upload_photo_error), Toast.LENGTH_SHORT)
            .show();
        return;
      }
      if (mimeType.equals("jpeg") || mimeType.equals("jpg")) {
        imageToUpload.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
      } else {
        imageToUpload.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
      }
      String encodedImage =
          android.util.Base64.encodeToString(
              byteArrayOutputStream.toByteArray(), android.util.Base64.NO_WRAP);

      if (getIntent().hasExtra("album_name")) {
        new Thread(
                new SendMessagesThread(
                    "UPLOAD_PHOTO",
                    MessageCodes.getRequest(),
                    getIntent().getExtras().getString("album_name")
                        + "\n"
                        + nameOfFile
                        + "\n"
                        + encodedImage))
            .start();
      }
    }
  }

  @Override
  public void onBackPressed() {
    Intent goMain = new Intent(getApplicationContext(), MainActivity.class);
    if (getIntent().hasExtra("albums")) {
      goMain.putExtra("albums", getIntent().getExtras().getString("albums"));
    }
    MySocket.setClosed(true);
    startActivity(goMain);
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

  @RequiresApi(api = Build.VERSION_CODES.O)
  @Override
  public void messageReceived(String mes, Activity activity) {
    HandelMessage message = new HandelMessage(mes);
    if (message.getName().equals("PHOTOS")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        generatePhotos(message.getData());
      } else if (message.getType().equals(MessageCodes.getPhotosError())) {
        Toast.makeText(
                getApplicationContext(),
                getResources().getString(R.string.photos_error),
                Toast.LENGTH_SHORT)
            .show();
      }
    }
    if (message.getName().equals("UPLOAD_PHOTO")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        addPhoto(message.getData());
      } else if (message.getType().equals(MessageCodes.getUploadPhotoError())) {
        Toast.makeText(
                getApplicationContext(),
                getResources().getString(R.string.upload_photo_error),
                Toast.LENGTH_SHORT)
            .show();
      }
    }
  }

  //    @Override
  //    public boolean onTouchEvent(MotionEvent ev) {
  //        try {
  //            return super.onTouchEvent(ev);
  //        } catch (IllegalArgumentException e) {
  //            //uncomment if you really want to see these errors
  //            //e.printStackTrace();
  //            return false;
  //        }
  //    }
  //
  //    @Override
  //    public boolean onInterceptTouchEvent(MotionEvent ev) {
  //        try {
  //            return super.onInterceptTouchEvent(ev);
  //        } catch (IllegalArgumentException e) {
  //            //uncomment if you really want to see these errors
  //            //e.printStackTrace();
  //            return false;
  //        }
  //    }
}
