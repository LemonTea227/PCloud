package com.example.pcloud;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
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
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import com.bumptech.glide.Glide;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.snackbar.Snackbar;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Objects;

public class PhotoViewerActivity extends AppCompatActivity implements ReceiveMessagesListener {
  PhotoView photoViewerPhotoView;
  private ImageView photoViewerGifImageView;
  private PlayerView photoViewerPlayerView;
  private ProgressBar photoViewerLoadingProgress;
  private Bitmap currentPhotoBitmap;
  private byte[] currentMediaBytes;
  private String currentMimeType = "image/jpeg";
  private ExoPlayer exoPlayer;
  private File currentTempMediaFile;
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
  private final ArrayList<String> orderedPhotoNames = new ArrayList<>();
  private int currentPhotoIndex = -1;
  private String requestedPhotoName = "";
  private String receivedDonePhotoName = "";
  private GestureDetector swipeGestureDetector;

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
    photoViewerGifImageView = findViewById(R.id.photoViewerGifImageView);
    photoViewerPlayerView = findViewById(R.id.photoViewerPlayerView);
    photoViewerLoadingProgress = findViewById(R.id.photoViewerLoadingProgress);
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
    swipeGestureDetector =
        new GestureDetector(
            this,
            new GestureDetector.SimpleOnGestureListener() {
              private static final int SWIPE_DISTANCE_THRESHOLD = 120;
              private static final int SWIPE_VELOCITY_THRESHOLD = 120;

              @Override
              public boolean onDown(MotionEvent e) {
                return true;
              }

              @Override
              public boolean onFling(
                  MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) {
                  return false;
                }
                float deltaX = e2.getX() - e1.getX();
                float deltaY = e2.getY() - e1.getY();
                if (Math.abs(deltaX) < Math.abs(deltaY)) {
                  return false;
                }
                if (Math.abs(deltaX) < SWIPE_DISTANCE_THRESHOLD
                    || Math.abs(velocityX) < SWIPE_VELOCITY_THRESHOLD) {
                  return false;
                }
                if (photoViewerPhotoView.getVisibility() == View.VISIBLE
                    && photoViewerPhotoView.getScale()
                        > photoViewerPhotoView.getMinimumScale() + 0.05f) {
                  return false;
                }
                if (deltaX < 0) {
                  return navigateToRelativePhoto(1);
                }
                return navigateToRelativePhoto(-1);
              }
            });
    View.OnTouchListener swipeTouchListener =
        (v, event) -> {
          if (swipeGestureDetector != null) {
            swipeGestureDetector.onTouchEvent(event);
          }
          return false;
        };
    photoViewerPhotoView.setOnTouchListener(swipeTouchListener);
    photoViewerGifImageView.setOnTouchListener(swipeTouchListener);
    photoViewerPlayerView.setOnTouchListener(swipeTouchListener);

    ArrayList<String> extraPhotoNames = getIntent().getStringArrayListExtra("photo_names");
    if (extraPhotoNames != null) {
      orderedPhotoNames.clear();
      orderedPhotoNames.addAll(extraPhotoNames);
    }
    currentPhotoIndex = getIntent().getIntExtra("photo_index", -1);
    if (currentPhotoIndex < 0 || currentPhotoIndex >= orderedPhotoNames.size()) {
      String currentName = getPhotoName();
      currentPhotoIndex = orderedPhotoNames.indexOf(currentName);
    }
    //        photoViewerImageView = findViewById(R.id.photoViewerImageView);

    if (getIntent().hasExtra("album_name")) {
      setTitle(getIntent().getExtras().getString("album_name"));
    }

    if (getIntent().hasExtra("album_name") && getIntent().hasExtra("photo_name")) {
      currentMimeType = MediaTypeUtil.detectMimeType(getIntent().getExtras().getString("photo_name"));
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
      if (MediaTypeUtil.isGifFileName(getPhotoName()) || MediaTypeUtil.isVideoFileName(getPhotoName())) {
        requestFullPhoto();
      } else {
        requestPreviewPhoto();
      }
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
      requestedPhotoName = getPhotoName();
      receivedDonePhotoName = "";
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

  private boolean navigateToRelativePhoto(int offset) {
    if (orderedPhotoNames.isEmpty() || currentPhotoIndex < 0) {
      return false;
    }
    int newIndex = currentPhotoIndex + offset;
    if (newIndex < 0 || newIndex >= orderedPhotoNames.size()) {
      return false;
    }
    currentPhotoIndex = newIndex;
    getIntent().putExtra("photo_index", currentPhotoIndex);
    getIntent().putExtra("photo_name", orderedPhotoNames.get(currentPhotoIndex));
    resetViewerStateForNavigation();
    String albumName =
        getIntent().hasExtra("album_name") ? getIntent().getStringExtra("album_name") : "";
    setTitle(albumName);
    byte[] previewBytes = null;
    if (albumName != null && !albumName.equals("")) {
      Bitmap preview =
          SessionDataCache.getAlbumPreviewBitmaps(albumName).get(orderedPhotoNames.get(currentPhotoIndex));
      if (preview != null) {
        ByteArrayOutputStream previewStream = new ByteArrayOutputStream();
        preview.compress(Bitmap.CompressFormat.JPEG, 70, previewStream);
        previewBytes = previewStream.toByteArray();
      }
    }
    if (previewBytes != null && previewBytes.length > 0) {
      getIntent().putExtra("preview_bytes", previewBytes);
      Bitmap previewBitmap = BitmapFactory.decodeByteArray(previewBytes, 0, previewBytes.length);
      if (previewBitmap != null) {
        setViewerModeImage();
        currentPhotoBitmap = previewBitmap;
        photoViewerPhotoView.setImageBitmap(previewBitmap);
      }
    } else {
      photoViewerPhotoView.setImageDrawable(null);
      photoViewerGifImageView.setImageDrawable(null);
    }
    currentMimeType = MediaTypeUtil.detectMimeType(getPhotoName());
    if (MediaTypeUtil.isGifFileName(getPhotoName()) || MediaTypeUtil.isVideoFileName(getPhotoName())) {
      requestFullPhoto();
    } else {
      requestPreviewPhoto();
    }
    return true;
  }

  private void resetViewerStateForNavigation() {
    stopVideoPlayback();
    hasFullPhoto = false;
    photoRequestInFlight = false;
    pendingShareAfterFetch = false;
    pendingDownloadAfterFetch = false;
    incomingPhotoChunksByPart.clear();
    expectedPhotoChunks = 0;
    currentPhotoBitmap = null;
    currentMediaBytes = null;
    photoViewerLoadingProgress.setVisibility(View.GONE);
  }

  private String getPhotoName() {
    return getIntent().hasExtra("photo_name") ? getIntent().getExtras().getString("photo_name") : "";
  }

  private void setViewerModeImage() {
    stopVideoPlayback();
    photoViewerPhotoView.setVisibility(View.VISIBLE);
    photoViewerGifImageView.setVisibility(View.GONE);
    photoViewerPlayerView.setVisibility(View.GONE);
  }

  private void setViewerModeGif() {
    stopVideoPlayback();
    photoViewerPhotoView.setVisibility(View.GONE);
    photoViewerGifImageView.setVisibility(View.VISIBLE);
    photoViewerPlayerView.setVisibility(View.GONE);
  }

  private void setViewerModeVideo() {
    photoViewerPhotoView.setVisibility(View.GONE);
    photoViewerGifImageView.setVisibility(View.GONE);
    photoViewerPlayerView.setVisibility(View.VISIBLE);
  }

  private void stopVideoPlayback() {
    if (exoPlayer != null) {
      exoPlayer.release();
      exoPlayer = null;
    }
    photoViewerPlayerView.setPlayer(null);
  }

  private File writeBytesToTempMediaFile(byte[] bytes, String fileName) {
    if (bytes == null || bytes.length == 0) {
      return null;
    }
    String safeName = fileName == null ? "media" : fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    if (safeName.equals("")) {
      safeName = "media";
    }
    File cacheDir = new File(getCacheDir(), "viewer_media");
    if (!cacheDir.exists() && !cacheDir.mkdirs()) {
      return null;
    }
    File file = new File(cacheDir, System.currentTimeMillis() + "_" + safeName);
    try {
      FileOutputStream outputStream = new FileOutputStream(file);
      outputStream.write(bytes);
      outputStream.flush();
      outputStream.close();
      return file;
    } catch (IOException e) {
      return null;
    }
  }

  private void playVideoBytes(byte[] bytes) {
    File mediaFile = writeBytesToTempMediaFile(bytes, getPhotoName());
    if (mediaFile == null) {
      Toast.makeText(getApplicationContext(), getString(R.string.photo_error), Toast.LENGTH_SHORT).show();
      return;
    }
    currentTempMediaFile = mediaFile;
    Uri mediaUri = Uri.fromFile(mediaFile);
    setViewerModeVideo();
    photoViewerLoadingProgress.setVisibility(View.VISIBLE);

    ExoPlayer player = new ExoPlayer.Builder(this).build();
    exoPlayer = player;
    photoViewerPlayerView.setPlayer(player);
    player.setMediaItem(MediaItem.fromUri(mediaUri));
    player.addListener(
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(int playbackState) {
            if (playbackState == Player.STATE_BUFFERING) {
              photoViewerLoadingProgress.setVisibility(View.VISIBLE);
            } else if (playbackState == Player.STATE_READY) {
              photoViewerLoadingProgress.setVisibility(View.GONE);
            } else if (playbackState == Player.STATE_ENDED) {
              photoViewerLoadingProgress.setVisibility(View.GONE);
            }
          }

          @Override
          public void onPlayerError(PlaybackException error) {
            photoViewerLoadingProgress.setVisibility(View.GONE);
            Toast.makeText(getApplicationContext(), getString(R.string.photo_error), Toast.LENGTH_SHORT)
                .show();
          }
        });
    player.prepare();
    player.setPlayWhenReady(true);
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
    if (!hasFullPhoto || (currentPhotoBitmap == null && (currentMediaBytes == null || currentMediaBytes.length == 0))) {
      pendingShareAfterFetch = true;
      Toast.makeText(
              getApplicationContext(), getString(R.string.downloading_photo), Toast.LENGTH_SHORT)
          .show();
      requestFullPhoto();
      return;
    }
    if (currentMediaBytes != null && currentMediaBytes.length > 0) {
      shareBytes(currentMediaBytes, currentMimeType);
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
    if (!hasFullPhoto || (currentPhotoBitmap == null && (currentMediaBytes == null || currentMediaBytes.length == 0))) {
      pendingDownloadAfterFetch = true;
      Toast.makeText(
              getApplicationContext(), getString(R.string.downloading_photo), Toast.LENGTH_SHORT)
          .show();
      requestFullPhoto();
      return;
    }
    if (currentMediaBytes != null && currentMediaBytes.length > 0) {
      saveCurrentMediaBytesToStorage();
      return;
    }
    saveCurrentPhotoToGallery();
  }

  private void shareBytes(byte[] bytes, String mimeType) {
    Uri uri = saveBytesForShare(bytes, getPhotoDisplayName());
    if (uri == null) {
      Toast.makeText(getApplicationContext(), getString(R.string.share_error), Toast.LENGTH_SHORT)
          .show();
      return;
    }
    Intent shareIntent = new Intent(Intent.ACTION_SEND);
    shareIntent.setType(mimeType == null || mimeType.equals("") ? "application/octet-stream" : mimeType);
    shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      shareIntent.setClipData(ClipData.newUri(getContentResolver(), "shared_media", uri));
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

  private android.net.Uri saveBytesForShare(byte[] bytes, String fileName) {
    File cacheDir = new File(getCacheDir(), "shared_images");
    if (!cacheDir.exists() && !cacheDir.mkdirs()) {
      return null;
    }
    String sanitizedName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    if (sanitizedName.equals("")) {
      sanitizedName = "media_" + System.currentTimeMillis();
    }
    File mediaFile = new File(cacheDir, sanitizedName);
    try {
      FileOutputStream outputStream = new FileOutputStream(mediaFile);
      outputStream.write(bytes);
      outputStream.flush();
      outputStream.close();
      return FileProvider.getUriForFile(
          this, getApplicationContext().getPackageName() + ".fileprovider", mediaFile);
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
    if (photoName == null || photoName.equals("")) {
      photoName = "photo_" + System.currentTimeMillis();
    }
    if (!photoName.contains(".")) {
      if (currentMimeType != null && currentMimeType.startsWith("video/")) {
        photoName += ".mp4";
      } else if ("image/gif".equals(currentMimeType)) {
        photoName += ".gif";
      } else {
        photoName += ".jpg";
      }
    }
    if (photoName != null && photoName.equals("")) {
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

  private void saveCurrentMediaBytesToStorage() {
    if (currentMediaBytes == null || currentMediaBytes.length == 0) {
      return;
    }
    File temp = writeBytesToTempMediaFile(currentMediaBytes, getPhotoDisplayName());
    if (temp == null) {
      Toast.makeText(getApplicationContext(), getString(R.string.share_error), Toast.LENGTH_SHORT)
          .show();
      return;
    }

    String mime = currentMimeType == null ? "application/octet-stream" : currentMimeType;
    if (mime.startsWith("video/")) {
      MediaScannerConnection.scanFile(
          this,
          new String[] {temp.getAbsolutePath()},
          new String[] {mime},
          null);
    } else {
      MediaScannerConnection.scanFile(
          this,
          new String[] {temp.getAbsolutePath()},
          new String[] {mime},
          null);
    }
    Toast.makeText(getApplicationContext(), getString(R.string.download_complete), Toast.LENGTH_SHORT)
        .show();
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
              byte[] decodedBytes = null;
              try {
                decodedBytes = Base64.getDecoder().decode(encodedPhoto);
                if (!MediaTypeUtil.isVideoFileName(getPhotoName())
                    && !MediaTypeUtil.isGifFileName(getPhotoName())) {
                  decoded = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                }
              } catch (IllegalArgumentException ignored) {
              }

              final Bitmap finalDecoded = decoded;
              final byte[] finalDecodedBytes = decodedBytes;
              runOnUiThread(
                  () -> {
                    if ((MediaTypeUtil.isVideoFileName(getPhotoName())
                            || MediaTypeUtil.isGifFileName(getPhotoName()))
                        && (finalDecodedBytes == null || finalDecodedBytes.length == 0)) {
                      if (currentRequestWantsFull) {
                        MySocket.endTransfer();
                        TransferNotificationHelper.failDownload(getApplicationContext());
                      }
                      photoRequestInFlight = false;
                      pendingShareAfterFetch = false;
                      pendingDownloadAfterFetch = false;
                      return;
                    }

                    if (!MediaTypeUtil.isVideoFileName(getPhotoName())
                        && !MediaTypeUtil.isGifFileName(getPhotoName())
                        && finalDecoded == null) {
                      if (currentRequestWantsFull) {
                        MySocket.endTransfer();
                        TransferNotificationHelper.failDownload(getApplicationContext());
                      }
                      photoRequestInFlight = false;
                      pendingShareAfterFetch = false;
                      pendingDownloadAfterFetch = false;
                      return;
                    }

                    currentMediaBytes = currentRequestWantsFull ? finalDecodedBytes : null;
                    if (MediaTypeUtil.isGifFileName(getPhotoName()) && currentRequestWantsFull) {
                      setViewerModeGif();
                      Glide.with(PhotoViewerActivity.this)
                          .asGif()
                          .load(finalDecodedBytes)
                          .into(photoViewerGifImageView);
                      currentPhotoBitmap = null;
                    } else if (MediaTypeUtil.isVideoFileName(getPhotoName()) && currentRequestWantsFull) {
                      currentPhotoBitmap = null;
                      playVideoBytes(finalDecodedBytes);
                    } else {
                      setViewerModeImage();
                      currentPhotoBitmap = finalDecoded;
                      photoViewerPhotoView.setImageBitmap(finalDecoded);
                    }

                    hasFullPhoto = currentRequestWantsFull;
                    if (currentRequestWantsFull) {
                      MySocket.endTransfer();
                      TransferNotificationHelper.completeDownload(getApplicationContext());
                    }
                    photoRequestInFlight = false;
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
          String messagePhotoName = lines[1];
          if (!messagePhotoName.equals(getPhotoName())) {
            return;
          }
          requestedPhotoName = messagePhotoName;
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
          String messagePhotoName = lines[1];
          if (!messagePhotoName.equals(requestedPhotoName) || !messagePhotoName.equals(getPhotoName())) {
            return;
          }
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
        String[] doneLines = message.getData().split("\n", 2);
        if (doneLines.length >= 2) {
          receivedDonePhotoName = doneLines[1];
          if (!receivedDonePhotoName.equals(requestedPhotoName)
              || !receivedDonePhotoName.equals(getPhotoName())) {
            incomingPhotoChunksByPart.clear();
            expectedPhotoChunks = 0;
            return;
          }
        }
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
    stopVideoPlayback();
    photoViewerLoadingProgress.setVisibility(View.GONE);
    if (pendingDeleteRunnable != null
        && pendingDeletePayload != null
        && !pendingDeletePayload.equals("")) {
      deleteHandler.removeCallbacks(pendingDeleteRunnable);
      sendPendingPhotoDeleteToServer();
      pendingDeleteRunnable = null;
    }
  }

  @Override
  protected void onDestroy() {
    stopVideoPlayback();
    Glide.with(this).clear(photoViewerGifImageView);
    if (currentTempMediaFile != null && currentTempMediaFile.exists()) {
      //noinspection ResultOfMethodCallIgnored
      currentTempMediaFile.delete();
    }
    super.onDestroy();
  }
}
