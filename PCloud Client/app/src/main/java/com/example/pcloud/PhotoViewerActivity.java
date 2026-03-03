package com.example.pcloud;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.github.chrisbanes.photoview.PhotoView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Objects;

public class PhotoViewerActivity extends AppCompatActivity implements ReceiveMessagesListener {
  PhotoView photoViewerPhotoView;
  private Bitmap currentPhotoBitmap;

  //    ImageView photoViewerImageView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_photo_viewer);
    java.text.DateFormat dateFormat =
        android.text.format.DateFormat.getDateFormat(getApplicationContext());

    ReceiveMessagesThread.setActivity(this);
    ReceiveMessagesThread.setListener(PhotoViewerActivity.this);

    new Thread(new ReceiveMessagesThread()).start();

    Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setDisplayShowHomeEnabled(true);

    photoViewerPhotoView = findViewById(R.id.photoViewerPhotoView);
    //        photoViewerImageView = findViewById(R.id.photoViewerImageView);

    if (getIntent().hasExtra("album_name")) {
      setTitle(getIntent().getExtras().getString("album_name"));
    }

    if (getIntent().hasExtra("album_name") && getIntent().hasExtra("photo_name")) {
      new Thread(
              new SendMessagesThread(
                  "PHOTO",
                  MessageCodes.getRequest(),
                  getIntent().getExtras().getString("album_name")
                      + "\n"
                      + getIntent().getExtras().getString("photo_name")))
          .start();
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.photo_viewer_menu, menu);
    return true;
  }

  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      onBackPressed();
    }

    switch (item.getItemId()) {
      case R.id.sharePhotoViewerMenuItem:
        shareCurrentPhoto();
        return true;
      case R.id.deletePhotoViewerMenuItem:
        deleteCurrentPhoto();
        return true;
      case R.id.zoomInPhotoViewerMenuItem:
        photoViewerPhotoView.setScale(photoViewerPhotoView.getScale() * 1.25f, true);
        return true;
      case R.id.zoomOutPhotoViewerMenuItem:
        photoViewerPhotoView.setScale(Math.max(1.0f, photoViewerPhotoView.getScale() / 1.25f), true);
        return true;
      case R.id.resetZoomPhotoViewerMenuItem:
        photoViewerPhotoView.setScale(1.0f, true);
        return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void shareCurrentPhoto() {
    if (currentPhotoBitmap == null) {
      Toast.makeText(getApplicationContext(), getString(R.string.share_error), Toast.LENGTH_SHORT).show();
      return;
    }
    String photoName = getIntent().hasExtra("photo_name") ? getIntent().getExtras().getString("photo_name") : "photo";
    android.net.Uri uri = saveBitmapForShare(currentPhotoBitmap, photoName);
    if (uri == null) {
      Toast.makeText(getApplicationContext(), getString(R.string.share_error), Toast.LENGTH_SHORT).show();
      return;
    }
    Intent shareIntent = new Intent(Intent.ACTION_SEND);
    shareIntent.setType("image/*");
    shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      shareIntent.setClipData(ClipData.newUri(getContentResolver(), "shared_photo", uri));
    }
    startActivity(Intent.createChooser(shareIntent, getString(R.string.share)));
  }

  private android.net.Uri saveBitmapForShare(Bitmap bitmap, String photoName) {
    File cacheDir = new File(getCacheDir(), "shared_images");
    if (!cacheDir.exists() && !cacheDir.mkdirs()) {
      return null;
    }
    String sanitizedName = photoName.replaceAll("[^a-zA-Z0-9._-]", "_");
    if (sanitizedName.equals("")) {
      sanitizedName = "photo_" + System.currentTimeMillis();
    }
    File imageFile = new File(cacheDir, sanitizedName + ".png");
    try {
      FileOutputStream outputStream = new FileOutputStream(imageFile);
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
      outputStream.flush();
      outputStream.close();
      return FileProvider.getUriForFile(
          this, getApplicationContext().getPackageName() + ".fileprovider", imageFile);
    } catch (IOException e) {
      return null;
    }
  }

  private void deleteCurrentPhoto() {
    if (!getIntent().hasExtra("album_name") || !getIntent().hasExtra("photo_name")) {
      return;
    }
    String payload =
        getIntent().getExtras().getString("album_name")
            + "\n"
            + getIntent().getExtras().getString("photo_name");
    new Thread(new SendMessagesThread("DEL_PHOTOS", MessageCodes.getRequest(), payload)).start();
  }

  @Override
  public void onBackPressed() {
    Intent goSecond = new Intent(getApplicationContext(), SecondActivity.class);
    if (getIntent().hasExtra("albums")) {
      goSecond.putExtra("albums", getIntent().getExtras().getString("albums"));
    }

    if (getIntent().hasExtra("album_name")) {
      goSecond.putExtra("album_name", getIntent().getExtras().getString("album_name"));
    }

    MySocket.setClosed(true);
    goSecond.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(goSecond);
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  @Override
  public void messageReceived(String mes, Activity activity) {
    HandelMessage message = new HandelMessage(mes);
    if (message.getName().equals("PHOTO")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        byte[] decodedString = Base64.getDecoder().decode(message.getData());
        Bitmap decoded = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
        currentPhotoBitmap = decoded;
        photoViewerPhotoView.setImageBitmap(decoded);
      } else if (message.getType().equals(MessageCodes.getPhotoError())) {
        Toast.makeText(
                getApplicationContext(),
                getResources().getString(R.string.photos_error),
                Toast.LENGTH_SHORT)
            .show();
      }
    }
    if (message.getName().equals("DEL_PHOTOS")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        Toast.makeText(getApplicationContext(), getString(R.string.photos_deleted), Toast.LENGTH_SHORT)
            .show();
        onBackPressed();
      } else if (message.getType().equals(MessageCodes.getDelPhotosError())) {
        Toast.makeText(getApplicationContext(), getString(R.string.delete_photo_error), Toast.LENGTH_SHORT)
            .show();
      }
    }
  }
}
