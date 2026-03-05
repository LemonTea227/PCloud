package com.example.pcloud;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.snackbar.Snackbar;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Objects;

public class PhotoViewerActivity extends AppCompatActivity implements ReceiveMessagesListener {
  PhotoView photoViewerPhotoView;
  private Bitmap currentPhotoBitmap;
  private final Handler deleteHandler = new Handler(Looper.getMainLooper());
  private Runnable pendingDeleteRunnable;
  private String pendingDeletePayload;
  private final HashMap<Integer, String> incomingPhotoChunksByPart = new HashMap<>();
  private int expectedPhotoChunks = 0;
  private boolean hasFullPhoto = false;
  private boolean photoRequestInFlight = false;
  private boolean pendingShareAfterFetch = false;
  private boolean pendingDownloadAfterFetch = false;
  private boolean currentRequestWantsFull = false;

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
    photoViewerPhotoView.setZoomable(true);
    photoViewerPhotoView.setMinimumScale(1.0f);
    photoViewerPhotoView.setMediumScale(2.5f);
    photoViewerPhotoView.setMaximumScale(5.0f);
    photoViewerPhotoView.setOnDoubleTapListener(
        new GestureDetector.SimpleOnGestureListener() {
          @Override
          public boolean onDoubleTap(android.view.MotionEvent e) {
            float current = photoViewerPhotoView.getScale();
            if (current < photoViewerPhotoView.getMediumScale()) {
              photoViewerPhotoView.setScale(
                  photoViewerPhotoView.getMediumScale(), e.getX(), e.getY(), true);
            } else if (current < photoViewerPhotoView.getMaximumScale()) {
              photoViewerPhotoView.setScale(
                  photoViewerPhotoView.getMaximumScale(), e.getX(), e.getY(), true);
            } else {
              photoViewerPhotoView.setScale(
                  photoViewerPhotoView.getMinimumScale(), e.getX(), e.getY(), true);
            }
            return true;
          }
        });
    //        photoViewerImageView = findViewById(R.id.photoViewerImageView);

    if (getIntent().hasExtra("album_name")) {
      setTitle(getIntent().getExtras().getString("album_name"));
    }

    if (getIntent().hasExtra("album_name") && getIntent().hasExtra("photo_name")) {
      if (getIntent().hasExtra("preview_bytes")) {
        byte[] preview = getIntent().getByteArrayExtra("preview_bytes");
        if (preview != null && preview.length > 0) {
          Bitmap previewBitmap = BitmapFactory.decodeByteArray(preview, 0, preview.length);
          if (previewBitmap != null) {
            currentPhotoBitmap = previewBitmap;
            photoViewerPhotoView.setImageBitmap(previewBitmap);
          }
        }
      }

      requestPreviewPhoto();
    }
  }

  private void requestPreviewPhoto() {
    requestPhoto(false);
  }

  private void requestFullPhoto() {
    requestPhoto(true);
  }

  private void requestPhoto(boolean wantFullPhoto) {
    if (photoRequestInFlight) {
      return;
    }
    if (getIntent().hasExtra("album_name") && getIntent().hasExtra("photo_name")) {
      photoRequestInFlight = true;
      currentRequestWantsFull = wantFullPhoto;
      if (wantFullPhoto) {
        MySocket.beginTransfer();
        TransferNotificationHelper.showDownloadProgress(getApplicationContext(), 0, 0);
      }
      expectedPhotoChunks = 0;
      incomingPhotoChunksByPart.clear();
      String payload =
          getIntent().getExtras().getString("album_name")
              + "\n"
              + getIntent().getExtras().getString("photo_name");
      if (!wantFullPhoto) {
        payload += "\nPREVIEW";
      }
      new Thread(new SendMessagesThread("PHOTO", MessageCodes.getRequest(), payload)).start();
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
      case R.id.downloadPhotoViewerMenuItem:
        downloadCurrentPhoto();
        return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void shareCurrentPhoto() {
    if (!hasFullPhoto || currentPhotoBitmap == null) {
      pendingShareAfterFetch = true;
      Toast.makeText(
              getApplicationContext(), getString(R.string.downloading_photo), Toast.LENGTH_SHORT)
          .show();
      requestFullPhoto();
      return;
    }
    shareBitmap(currentPhotoBitmap);
  }

  private void shareBitmap(Bitmap bitmapToShare) {
    String photoName =
        getIntent().hasExtra("photo_name")
            ? getIntent().getExtras().getString("photo_name")
            : "photo";
    android.net.Uri uri = saveBitmapForShare(bitmapToShare, photoName);
    if (uri == null) {
      Toast.makeText(getApplicationContext(), getString(R.string.share_error), Toast.LENGTH_SHORT)
          .show();
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

  private void downloadCurrentPhoto() {
    if (!hasFullPhoto || currentPhotoBitmap == null) {
      pendingDownloadAfterFetch = true;
      Toast.makeText(
              getApplicationContext(), getString(R.string.downloading_photo), Toast.LENGTH_SHORT)
          .show();
      requestFullPhoto();
      return;
    }
    saveCurrentPhotoToGallery();
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
    pendingDeletePayload = payload;

    if (pendingDeleteRunnable != null) {
      deleteHandler.removeCallbacks(pendingDeleteRunnable);
    }

    pendingDeleteRunnable = this::sendPendingPhotoDeleteToServer;
    deleteHandler.postDelayed(pendingDeleteRunnable, 5000);

    Snackbar.make(photoViewerPhotoView, getString(R.string.photos_deleted), Snackbar.LENGTH_LONG)
        .setDuration(5000)
        .setAction(
            getString(R.string.redo),
            v -> {
              if (pendingDeleteRunnable != null) {
                deleteHandler.removeCallbacks(pendingDeleteRunnable);
                pendingDeleteRunnable = null;
              }
              pendingDeletePayload = null;
            })
        .show();
  }

  private void sendPendingPhotoDeleteToServer() {
    if (pendingDeleteRunnable != null) {
      deleteHandler.removeCallbacks(pendingDeleteRunnable);
      pendingDeleteRunnable = null;
    }
    if (pendingDeletePayload == null || pendingDeletePayload.equals("")) {
      return;
    }
    new Thread(
            new SendMessagesThread("DEL_PHOTOS", MessageCodes.getRequest(), pendingDeletePayload))
        .start();
  }

  private void completePendingPhotoActionsIfReady() {
    if (!hasFullPhoto || currentPhotoBitmap == null) {
      return;
    }
    if (pendingDownloadAfterFetch) {
      pendingDownloadAfterFetch = false;
      saveCurrentPhotoToGallery();
    }
    if (pendingShareAfterFetch) {
      pendingShareAfterFetch = false;
      shareBitmap(currentPhotoBitmap);
    }
  }

  private String getPhotoDisplayName() {
    String photoName =
        getIntent().hasExtra("photo_name")
            ? getIntent().getExtras().getString("photo_name")
            : "photo_" + System.currentTimeMillis();
    if (!photoName.contains(".")) {
      photoName += ".jpg";
    }
    return photoName;
  }

  private void saveCurrentPhotoToGallery() {
    if (currentPhotoBitmap == null) {
      return;
    }
    boolean saved = saveBitmapToGallery(currentPhotoBitmap, getPhotoDisplayName());
    if (saved) {
      Toast.makeText(
              getApplicationContext(), getString(R.string.download_complete), Toast.LENGTH_SHORT)
          .show();
    } else {
      Toast.makeText(getApplicationContext(), getString(R.string.share_error), Toast.LENGTH_SHORT)
          .show();
    }
  }

  private boolean saveBitmapToGallery(Bitmap bitmap, String fileName) {
    OutputStream outputStream = null;
    try {
      ContentValues values = new ContentValues();
      values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
      values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        values.put(
            MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PCloud");
      }
      Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
      if (uri == null) {
        return false;
      }
      outputStream = getContentResolver().openOutputStream(uri);
      if (outputStream == null) {
        return false;
      }
      boolean compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream);
      outputStream.flush();
      return compressed;
    } catch (IOException e) {
      return false;
    } finally {
      if (outputStream != null) {
        try {
          outputStream.close();
        } catch (IOException ignored) {
        }
      }
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  private void decodeAndApplyFullPhotoAsync(final String encodedPhoto) {
    new Thread(
            () -> {
              Bitmap decoded = null;
              try {
                byte[] decodedString = Base64.getDecoder().decode(encodedPhoto);
                decoded = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
              } catch (IllegalArgumentException ignored) {
              }

              final Bitmap finalDecoded = decoded;
              runOnUiThread(
                  () -> {
                    if (finalDecoded == null) {
                      if (currentRequestWantsFull) {
                        MySocket.endTransfer();
                        TransferNotificationHelper.failDownload(getApplicationContext());
                      }
                      photoRequestInFlight = false;
                      pendingShareAfterFetch = false;
                      pendingDownloadAfterFetch = false;
                      return;
                    }
                    currentPhotoBitmap = finalDecoded;
                    hasFullPhoto = currentRequestWantsFull;
                    if (currentRequestWantsFull) {
                      MySocket.endTransfer();
                      TransferNotificationHelper.completeDownload(getApplicationContext());
                    }
                    photoRequestInFlight = false;
                    photoViewerPhotoView.setImageBitmap(finalDecoded);
                    completePendingPhotoActionsIfReady();
                  });
            })
        .start();
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
    goSecond.putExtra("restore_cached_previews", true);

    goSecond.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(goSecond);
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  @Override
  public void messageReceived(String mes, Activity activity) {
    HandelMessage message = new HandelMessage(mes);
    if (message.getName().equals("PHOTO")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        decodeAndApplyFullPhotoAsync(message.getData());
      } else if (message.getType().equals(MessageCodes.getPhotoError())) {
        if (currentRequestWantsFull) {
          MySocket.endTransfer();
          TransferNotificationHelper.failDownload(getApplicationContext());
        }
        photoRequestInFlight = false;
        pendingShareAfterFetch = false;
        pendingDownloadAfterFetch = false;
        Toast.makeText(
                getApplicationContext(),
                getResources().getString(R.string.photos_error),
                Toast.LENGTH_SHORT)
            .show();
      }
    }

    if (message.getName().equals("PHOTO_COUNT")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        String[] lines = message.getData().split("\n");
        if (lines.length >= 3) {
          try {
            expectedPhotoChunks = Integer.parseInt(lines[2]);
          } catch (NumberFormatException ignored) {
            expectedPhotoChunks = 0;
          }
          if (currentRequestWantsFull) {
            TransferNotificationHelper.showDownloadProgress(
                getApplicationContext(), 0, expectedPhotoChunks);
          }
          incomingPhotoChunksByPart.clear();
        }
      }
    }

    if (message.getName().equals("PHOTO_CHUNK")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        String[] lines = message.getData().split("\n", 5);
        if (lines.length >= 5) {
          try {
            int chunkIndex = Integer.parseInt(lines[2]);
            incomingPhotoChunksByPart.put(chunkIndex, lines[4]);
            if (currentRequestWantsFull && expectedPhotoChunks > 0) {
              TransferNotificationHelper.showDownloadProgress(
                  getApplicationContext(), incomingPhotoChunksByPart.size(), expectedPhotoChunks);
            }
          } catch (NumberFormatException ignored) {
          }
        }
      }
    }

    if (message.getName().equals("PHOTO_DONE")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        if (expectedPhotoChunks > 0) {
          StringBuilder assembled = new StringBuilder();
          for (int index = 0; index < expectedPhotoChunks; index++) {
            String chunk = incomingPhotoChunksByPart.get(index);
            if (chunk == null) {
              if (currentRequestWantsFull) {
                MySocket.endTransfer();
                TransferNotificationHelper.failDownload(getApplicationContext());
              }
              photoRequestInFlight = false;
              pendingShareAfterFetch = false;
              pendingDownloadAfterFetch = false;
              incomingPhotoChunksByPart.clear();
              expectedPhotoChunks = 0;
              return;
            }
            assembled.append(chunk);
          }
          incomingPhotoChunksByPart.clear();
          expectedPhotoChunks = 0;
          decodeAndApplyFullPhotoAsync(assembled.toString());
        } else {
          if (currentRequestWantsFull) {
            MySocket.endTransfer();
            TransferNotificationHelper.completeDownload(getApplicationContext());
          }
          photoRequestInFlight = false;
          if (pendingDownloadAfterFetch) {
            pendingDownloadAfterFetch = false;
            Toast.makeText(
                    getApplicationContext(),
                    getString(R.string.download_complete),
                    Toast.LENGTH_SHORT)
                .show();
          }
        }
      }
    }

    if (message.getName().equals("DEL_PHOTOS")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        if (pendingDeleteRunnable != null) {
          deleteHandler.removeCallbacks(pendingDeleteRunnable);
          pendingDeleteRunnable = null;
        }
        pendingDeletePayload = null;
        Toast.makeText(
                getApplicationContext(), getString(R.string.photos_deleted), Toast.LENGTH_SHORT)
            .show();
        onBackPressed();
      } else if (message.getType().equals(MessageCodes.getDelPhotosError())) {
        if (pendingDeleteRunnable != null) {
          deleteHandler.removeCallbacks(pendingDeleteRunnable);
          pendingDeleteRunnable = null;
        }
        pendingDeletePayload = null;
        Toast.makeText(
                getApplicationContext(), getString(R.string.delete_photo_error), Toast.LENGTH_SHORT)
            .show();
      }
    }
  }

  @Override
  protected void onStop() {
    super.onStop();
    if (pendingDeleteRunnable != null
        && pendingDeletePayload != null
        && !pendingDeletePayload.equals("")) {
      deleteHandler.removeCallbacks(pendingDeleteRunnable);
      sendPendingPhotoDeleteToServer();
      pendingDeleteRunnable = null;
    }
  }
}
