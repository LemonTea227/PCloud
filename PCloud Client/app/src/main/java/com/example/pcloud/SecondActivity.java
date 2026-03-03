package com.example.pcloud;

import android.app.Activity;
import android.content.ClipData;
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
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class SecondActivity extends AppCompatActivity
    implements ReceiveMessagesListener, PhotosAdapter.PhotoInteractionListener {
  private static final int PREVIEW_MAX_DIMENSION = 320;
  private RecyclerView photosSecondRecyclerView;
  private RecyclerView.Adapter adapter;
  private RecyclerView.LayoutManager layoutManager;
  private static final int RESULT_LOAD_IMAGE = 1;
  private String mimeType;
  private String nameOfFile;

  private ArrayList<PhotosItem> photos;
  private final LinkedHashSet<String> selectedPhotoNames = new LinkedHashSet<>();
  private LinkedHashSet<String> pendingDeletePhotoNames = new LinkedHashSet<>();
  private final Map<String, Bitmap> previewBitmapsByName = new ConcurrentHashMap<>();
  private Menu optionsMenu;
  private boolean selectionMode;

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
    selectionMode = false;

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
              getIntent().getExtras().getString("album_name"),
              this);
    } else {
      adapter = new PhotosAdapter(getApplicationContext(), this.photos, "", "", this);
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
      previewBitmapsByName.put(photoName, decodedPhoto);
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
          previewBitmapsByName.put(parts[0], decodedPreview);
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
    this.optionsMenu = menu;
    if (first) {
      MenuInflater inflater = getMenuInflater();
      inflater.inflate(R.menu.photos_menu, menu);
      first = false;
    }
    refreshSelectionUi();
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
      case R.id.selectPhotosMenuItem:
        selectionMode = !selectionMode;
        if (!selectionMode) {
          selectedPhotoNames.clear();
        }
        refreshSelectionUi();
        adapter.notifyDataSetChanged();
        return true;
      case R.id.sharePhotosMenuItem:
        shareSelectedPhotos();
        return true;
      case R.id.deletePhotosMenuItem:
        deleteSelectedPhotos();
        return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onOpenPhoto(String photoName) {
    Intent goPhotoViewer = new Intent(getApplicationContext(), PhotoViewerActivity.class);
    goPhotoViewer.putExtra("album_name", getAlbumName());
    goPhotoViewer.putExtra("albums", getAlbumsPayload());
    goPhotoViewer.putExtra("photo_name", photoName);
    MySocket.setClosed(true);
    goPhotoViewer.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(goPhotoViewer);
  }

  @Override
  public void onTogglePhotoSelection(String photoName) {
    if (!selectionMode) {
      selectionMode = true;
    }
    if (selectedPhotoNames.contains(photoName)) {
      selectedPhotoNames.remove(photoName);
    } else {
      selectedPhotoNames.add(photoName);
    }
    refreshSelectionUi();
    adapter.notifyDataSetChanged();
  }

  @Override
  public boolean isSelectionModeEnabled() {
    return selectionMode;
  }

  @Override
  public boolean isPhotoSelected(String photoName) {
    return selectedPhotoNames.contains(photoName);
  }

  private void refreshSelectionUi() {
    if (optionsMenu == null) {
      return;
    }
    MenuItem selectItem = optionsMenu.findItem(R.id.selectPhotosMenuItem);
    MenuItem shareItem = optionsMenu.findItem(R.id.sharePhotosMenuItem);
    MenuItem deleteItem = optionsMenu.findItem(R.id.deletePhotosMenuItem);
    if (selectItem != null) {
      selectItem.setTitle(
          selectionMode
              ? getString(R.string.cancel_selection)
              : getString(R.string.select));
    }
    boolean hasSelection = !selectedPhotoNames.isEmpty();
    if (shareItem != null) {
      shareItem.setVisible(selectionMode);
      shareItem.setEnabled(hasSelection);
    }
    if (deleteItem != null) {
      deleteItem.setVisible(selectionMode);
      deleteItem.setEnabled(hasSelection);
    }
  }

  private String getAlbumName() {
    return getIntent().hasExtra("album_name") ? getIntent().getExtras().getString("album_name") : "";
  }

  private String getAlbumsPayload() {
    return getIntent().hasExtra("albums") ? getIntent().getExtras().getString("albums") : "";
  }

  private void shareSelectedPhotos() {
    if (selectedPhotoNames.isEmpty()) {
      Toast.makeText(getApplicationContext(), getString(R.string.no_photos_selected), Toast.LENGTH_SHORT)
          .show();
      return;
    }

    ArrayList<android.net.Uri> shareUris = new ArrayList<>();
    for (String photoName : selectedPhotoNames) {
      Bitmap bitmap = previewBitmapsByName.get(photoName);
      if (bitmap == null) {
        continue;
      }
      android.net.Uri uri = saveBitmapForShare(bitmap, photoName);
      if (uri != null) {
        shareUris.add(uri);
      }
    }

    if (shareUris.isEmpty()) {
      Toast.makeText(getApplicationContext(), getString(R.string.share_error), Toast.LENGTH_SHORT).show();
      return;
    }

    Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
    shareIntent.setType("image/*");
    shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareUris);
    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      ClipData clipData = ClipData.newUri(getContentResolver(), "shared_photo", shareUris.get(0));
      for (int i = 1; i < shareUris.size(); i++) {
        clipData.addItem(new ClipData.Item(shareUris.get(i)));
      }
      shareIntent.setClipData(clipData);
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

  private void deleteSelectedPhotos() {
    if (selectedPhotoNames.isEmpty()) {
      Toast.makeText(getApplicationContext(), getString(R.string.no_photos_selected), Toast.LENGTH_SHORT)
          .show();
      return;
    }
    StringBuilder payload = new StringBuilder(getAlbumName());
    for (String photoName : selectedPhotoNames) {
      payload.append("\n").append(photoName);
    }
    pendingDeletePhotoNames = new LinkedHashSet<>(selectedPhotoNames);
    new Thread(new SendMessagesThread("DEL_PHOTOS", MessageCodes.getRequest(), payload.toString()))
        .start();
  }

  private void removePhotosByName(List<String> photoNames) {
    if (photoNames == null || photoNames.isEmpty()) {
      return;
    }
    LinkedHashSet<String> toDelete = new LinkedHashSet<>(photoNames);
    ArrayList<PhotosItem> rebuilt = new ArrayList<>();
    ArrayList<String> remainingNames = new ArrayList<>();
    ArrayList<Bitmap> remainingBitmaps = new ArrayList<>();

    for (PhotosItem row : photos) {
      addIfRemains(row.getFirstName(), row.getFirstPhotoIcon(), toDelete, remainingNames, remainingBitmaps);
      addIfRemains(
          row.getSecondName(), row.getSecondPhotoIcon(), toDelete, remainingNames, remainingBitmaps);
      addIfRemains(row.getThirdName(), row.getThirdPhotoIcon(), toDelete, remainingNames, remainingBitmaps);
      addIfRemains(
          row.getFourtName(), row.getFourthPhotoIcon(), toDelete, remainingNames, remainingBitmaps);
    }

    for (int i = 0; i < remainingNames.size(); i += 4) {
      String firstName = remainingNames.get(i);
      Bitmap firstBitmap = remainingBitmaps.get(i);
      if (i + 1 < remainingNames.size()) {
        String secondName = remainingNames.get(i + 1);
        Bitmap secondBitmap = remainingBitmaps.get(i + 1);
        if (i + 2 < remainingNames.size()) {
          String thirdName = remainingNames.get(i + 2);
          Bitmap thirdBitmap = remainingBitmaps.get(i + 2);
          if (i + 3 < remainingNames.size()) {
            String fourthName = remainingNames.get(i + 3);
            Bitmap fourthBitmap = remainingBitmaps.get(i + 3);
            rebuilt.add(
                new PhotosItem(
                    firstName,
                    firstBitmap,
                    secondName,
                    secondBitmap,
                    thirdName,
                    thirdBitmap,
                    fourthName,
                    fourthBitmap));
          } else {
            rebuilt.add(
                new PhotosItem(
                    firstName,
                    firstBitmap,
                    secondName,
                    secondBitmap,
                    thirdName,
                    thirdBitmap));
          }
        } else {
          rebuilt.add(new PhotosItem(firstName, firstBitmap, secondName, secondBitmap));
        }
      } else {
        rebuilt.add(new PhotosItem(firstName, firstBitmap));
      }
    }

    photos.clear();
    photos.addAll(rebuilt);
    for (String deletedName : toDelete) {
      previewBitmapsByName.remove(deletedName);
    }
    adapter.notifyDataSetChanged();
  }

  private void addIfRemains(
      String name,
      Bitmap bitmap,
      LinkedHashSet<String> toDelete,
      ArrayList<String> remainingNames,
      ArrayList<Bitmap> remainingBitmaps) {
    if (bitmap == null || name == null || name.equals("")) {
      return;
    }
    if (!toDelete.contains(name)) {
      remainingNames.add(name);
      remainingBitmaps.add(bitmap);
    }
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
    if (message.getName().equals("DEL_PHOTOS")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        removePhotosByName(new ArrayList<>(pendingDeletePhotoNames));
        selectedPhotoNames.clear();
        pendingDeletePhotoNames.clear();
        selectionMode = false;
        refreshSelectionUi();
        Toast.makeText(getApplicationContext(), getString(R.string.photos_deleted), Toast.LENGTH_SHORT)
            .show();
      } else if (message.getType().equals(MessageCodes.getDelPhotosError())) {
        pendingDeletePhotoNames.clear();
        Toast.makeText(getApplicationContext(), getString(R.string.delete_photo_error), Toast.LENGTH_SHORT)
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
