package com.example.pcloud;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaDataSource;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
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
import com.google.android.material.snackbar.Snackbar;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
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
  private final Map<String, String> videoDurationByName = new ConcurrentHashMap<>();
  private final Map<Integer, Map<Integer, String>> incomingPreviewChunksByIndex = new HashMap<>();
  private final Map<Integer, Integer> incomingPreviewPartsByIndex = new HashMap<>();
  private final Map<Integer, String> incomingPreviewNameByIndex = new HashMap<>();
  private int expectedPreviewCount;
  private int previewPlaceholderOffset;
  private Menu optionsMenu;
  private boolean selectionMode;
  private final Handler deleteHandler = new Handler(Looper.getMainLooper());
  private Runnable pendingDeleteRunnable;
  private String pendingDeletePayload;
  private Snackbar loadingSnackbar;
  private PowerManager.WakeLock transferWakeLock;

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
    expectedPreviewCount = 0;
    previewPlaceholderOffset = 0;
    pendingDeletePayload = null;

    if (getIntent().hasExtra("album_name")) {
      setTitle(getIntent().getExtras().getString("album_name"));
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

    String albumName = getAlbumName();
    if (!albumName.equals("")) {
      boolean restoreCachedPreviews = getIntent().getBooleanExtra("restore_cached_previews", false);
      if (restoreCachedPreviews) {
        restoreCachedPhotos(albumName);
      } else {
        SessionDataCache.clearAlbumPreviewCache(albumName);
        previewBitmapsByName.clear();
        videoDurationByName.clear();
        photos.clear();
        adapter.notifyDataSetChanged();
      }
      acquireTransferWakeLock();
      showLoadingIndicator();
      new Thread(
              new SendMessagesThread(
                  "PHOTOS",
                  MessageCodes.getRequest(),
                  buildPhotosDeltaRequestPayload(albumName, restoreCachedPreviews)))
          .start();
    }
  }

  private void showLoadingIndicator() {
    if (photosSecondRecyclerView == null) {
      return;
    }
    if (loadingSnackbar != null) {
      loadingSnackbar.dismiss();
    }
    loadingSnackbar =
        Snackbar.make(
            photosSecondRecyclerView,
            getString(R.string.refreshing_from_server),
            Snackbar.LENGTH_INDEFINITE);
    loadingSnackbar.show();
  }

  private void dismissLoadingIndicator() {
    if (loadingSnackbar != null) {
      loadingSnackbar.dismiss();
      loadingSnackbar = null;
    }
    releaseTransferWakeLock();
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  private void addPhoto(String message) {
    String[] parts = message.split("~", 2);
    if (parts.length < 2) {
      return;
    }
    String photoName = parts[0];
    if (SessionDataCache.isPhotoPendingDeletion(getAlbumName(), photoName)) {
      return;
    }
    decodeAndApplyPreviewAsync(-1, photoName, parts[1]);
  }

  private boolean updatePhotoBitmapIfExists(String photoName, Bitmap bitmap) {
    if (photoName == null || bitmap == null) {
      return false;
    }
    for (int rowIndex = 0; rowIndex < photos.size(); rowIndex++) {
      PhotosItem row = photos.get(rowIndex);
      if (photoName.equals(row.getFirstName())) {
        row.setFirstPhotoIcon(bitmap);
        adapter.notifyItemChanged(rowIndex);
        return true;
      }
      if (photoName.equals(row.getSecondName())) {
        row.setSecondPhotoIcon(bitmap);
        adapter.notifyItemChanged(rowIndex);
        return true;
      }
      if (photoName.equals(row.getThirdName())) {
        row.setThirdPhotoIcon(bitmap);
        adapter.notifyItemChanged(rowIndex);
        return true;
      }
      if (photoName.equals(row.getFourtName())) {
        row.setFourthPhotoIcon(bitmap);
        adapter.notifyItemChanged(rowIndex);
        return true;
      }
    }
    return false;
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  private void generatePhotos(String message) {
    if (message != null && !message.equals("")) {
      String[] photosMessage = message.split("\n");
      for (int i = 0; i < photosMessage.length; i++) {
        String[] parts = photosMessage[i].split("~", 2);
        if (parts.length < 2) {
          continue;
        }
        if (SessionDataCache.isPhotoPendingDeletion(getAlbumName(), parts[0])) {
          continue;
        }
        Bitmap decodedPreview = decodePreviewBitmap(parts[1]);
        String durationLabel = "";
        if (decodedPreview == null && MediaTypeUtil.isVideoFileName(parts[0])) {
          VideoPreviewInfo previewInfo = decodeVideoPreviewInfoFromEncoded(parts[1]);
          decodedPreview = previewInfo.previewBitmap;
          durationLabel = previewInfo.durationLabel;
        }
        if (decodedPreview != null) {
          previewBitmapsByName.put(parts[0], decodedPreview);
          if (MediaTypeUtil.isVideoFileName(parts[0]) && !durationLabel.equals("")) {
            videoDurationByName.put(parts[0], durationLabel);
            SessionDataCache.putAlbumPhotoVideoDuration(getAlbumName(), parts[0], durationLabel);
          }
          appendPhotoToRows(parts[0], decodedPreview);
          SessionDataCache.putAlbumPhotoPreview(getAlbumName(), parts[0], decodedPreview);
        }
      }
    }
  }

  private void restoreCachedPhotos(String albumName) {
    java.util.LinkedHashMap<String, Bitmap> cachedPreviews =
        SessionDataCache.getAlbumPreviewBitmaps(albumName);
    if (cachedPreviews.isEmpty()) {
      return;
    }
    for (Map.Entry<String, Bitmap> entry : cachedPreviews.entrySet()) {
      String photoName = entry.getKey();
      Bitmap preview = entry.getValue();
      if (photoName == null || preview == null || previewBitmapsByName.containsKey(photoName)) {
        continue;
      }
      if (SessionDataCache.isPhotoPendingDeletion(albumName, photoName)) {
        continue;
      }
      previewBitmapsByName.put(photoName, preview);
      String cachedDuration = SessionDataCache.getAlbumPhotoVideoDuration(albumName, photoName);
      if (!cachedDuration.equals("")) {
        videoDurationByName.put(photoName, cachedDuration);
      }
      appendPhotoToRows(photoName, preview);
    }
  }

  private String buildPhotosDeltaRequestPayload(String albumName, boolean allowDelta) {
    if (!allowDelta) {
      return albumName;
    }
    List<String> knownPhotoNames = SessionDataCache.getAlbumPhotoNames(albumName);
    if (knownPhotoNames.isEmpty()) {
      return albumName;
    }
    StringBuilder payload = new StringBuilder(albumName).append("\nDELTA");
    for (String knownPhotoName : knownPhotoNames) {
      if (knownPhotoName == null || knownPhotoName.trim().equals("")) {
        continue;
      }
      payload.append("\n").append(knownPhotoName);
    }
    return payload.toString();
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

  private Bitmap downscaleForUpload(Bitmap source, int maxDimension) {
    if (source == null) {
      return null;
    }
    int width = source.getWidth();
    int height = source.getHeight();
    if (width <= maxDimension && height <= maxDimension) {
      return source;
    }

    float scale = Math.min((float) maxDimension / width, (float) maxDimension / height);
    int outWidth = Math.max(1, Math.round(width * scale));
    int outHeight = Math.max(1, Math.round(height * scale));
    return Bitmap.createScaledBitmap(source, outWidth, outHeight, true);
  }

  private void sendUploadInChunks(String albumName, String fileName, String encodedImage) {
    new Thread(
            () -> {
              if (albumName == null || fileName == null || encodedImage == null) {
                return;
              }
              int chunkSize = 7000;
              int totalParts = (encodedImage.length() + chunkSize - 1) / chunkSize;
              if (totalParts <= 0) {
                MySocket.endTransfer();
                TransferNotificationHelper.failUpload(getApplicationContext());
                return;
              }

              String startPayload = albumName + "\n" + fileName + "\n" + totalParts;
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
                TransferNotificationHelper.showUploadProgress(
                    getApplicationContext(), partIndex + 1, totalParts);
              }
            })
        .start();
  }

  private void acquireTransferWakeLock() {
    if (transferWakeLock != null && transferWakeLock.isHeld()) {
      return;
    }
    PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
    if (powerManager == null) {
      return;
    }
    transferWakeLock =
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pcloud:transfer-lock");
    transferWakeLock.setReferenceCounted(false);
    transferWakeLock.acquire(10 * 60 * 1000L);
  }

  private void releaseTransferWakeLock() {
    if (transferWakeLock != null && transferWakeLock.isHeld()) {
      transferWakeLock.release();
    }
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
        goGallery.setType("*/*");
        goGallery.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"image/*", "video/*"});
        startActivityForResult(goGallery, RESULT_LOAD_IMAGE);
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
    ArrayList<String> orderedPhotoNames = getOrderedPhotoNames();
    goPhotoViewer.putStringArrayListExtra("photo_names", orderedPhotoNames);
    int currentPhotoIndex = orderedPhotoNames.indexOf(photoName);
    if (currentPhotoIndex >= 0) {
      goPhotoViewer.putExtra("photo_index", currentPhotoIndex);
    }
    Bitmap previewBitmap = previewBitmapsByName.get(photoName);
    if (previewBitmap != null) {
      ByteArrayOutputStream previewStream = new ByteArrayOutputStream();
      previewBitmap.compress(Bitmap.CompressFormat.JPEG, 70, previewStream);
      goPhotoViewer.putExtra("preview_bytes", previewStream.toByteArray());
    }
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
    if (selectedPhotoNames.isEmpty()) {
      selectionMode = false;
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

  @Override
  public String getVideoDurationLabel(String photoName) {
    if (photoName == null) {
      return "";
    }
    String label = videoDurationByName.get(photoName);
    return label == null ? "" : label;
  }

  private ArrayList<String> getOrderedPhotoNames() {
    ArrayList<String> names = new ArrayList<>();
    for (PhotosItem row : photos) {
      addPhotoNameIfPresent(names, row.getFirstName(), row.getFirstPhotoIcon());
      addPhotoNameIfPresent(names, row.getSecondName(), row.getSecondPhotoIcon());
      addPhotoNameIfPresent(names, row.getThirdName(), row.getThirdPhotoIcon());
      addPhotoNameIfPresent(names, row.getFourtName(), row.getFourthPhotoIcon());
    }
    return names;
  }

  private void addPhotoNameIfPresent(ArrayList<String> names, String name, Bitmap bitmap) {
    if (name == null || name.equals("") || bitmap == null || name.startsWith("__loading__")) {
      return;
    }
    names.add(name);
  }

  private void refreshSelectionUi() {
    if (optionsMenu == null) {
      return;
    }
    MenuItem addItem = optionsMenu.findItem(R.id.addPhotosMenuItem);
    MenuItem shareItem = optionsMenu.findItem(R.id.sharePhotosMenuItem);
    MenuItem deleteItem = optionsMenu.findItem(R.id.deletePhotosMenuItem);
    boolean hasSelection = !selectedPhotoNames.isEmpty();
    if (addItem != null) {
      addItem.setVisible(!selectionMode);
    }
    if (shareItem != null) {
      shareItem.setVisible(selectionMode && hasSelection);
      shareItem.setEnabled(hasSelection);
    }
    if (deleteItem != null) {
      deleteItem.setVisible(selectionMode && hasSelection);
      deleteItem.setEnabled(hasSelection);
    }
  }

  private String getAlbumName() {
    return getIntent().hasExtra("album_name")
        ? getIntent().getExtras().getString("album_name")
        : "";
  }

  private String getAlbumsPayload() {
    return getIntent().hasExtra("albums") ? getIntent().getExtras().getString("albums") : "";
  }

  private void shareSelectedPhotos() {
    if (selectedPhotoNames.isEmpty()) {
      Toast.makeText(
              getApplicationContext(), getString(R.string.no_photos_selected), Toast.LENGTH_SHORT)
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
      Toast.makeText(getApplicationContext(), getString(R.string.share_error), Toast.LENGTH_SHORT)
          .show();
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
      Toast.makeText(
              getApplicationContext(), getString(R.string.no_photos_selected), Toast.LENGTH_SHORT)
          .show();
      return;
    }
    StringBuilder payload = new StringBuilder(getAlbumName());
    for (String photoName : selectedPhotoNames) {
      payload.append("\n").append(photoName);
    }
    pendingDeletePhotoNames = new LinkedHashSet<>(selectedPhotoNames);
    ArrayList<String> pendingDeleteNamesList = new ArrayList<>(pendingDeletePhotoNames);
    SessionDataCache.markAlbumPhotosPendingDeletion(getAlbumName(), pendingDeleteNamesList);
    SessionDataCache.removeAlbumPhotoPreviews(getAlbumName(), pendingDeleteNamesList);

    removePhotosByName(new ArrayList<>(pendingDeletePhotoNames));
    selectedPhotoNames.clear();
    selectionMode = false;
    refreshSelectionUi();

    pendingDeletePayload = payload.toString();
    pendingDeleteRunnable = this::sendPendingPhotoDeleteToServer;
    deleteHandler.postDelayed(pendingDeleteRunnable, 300);

    Snackbar.make(
            photosSecondRecyclerView, getString(R.string.photos_deleted), Snackbar.LENGTH_LONG)
        .setDuration(5000)
        .setAction(
            getString(R.string.redo),
            v -> {
              if (pendingDeleteRunnable != null) {
                deleteHandler.removeCallbacks(pendingDeleteRunnable);
                pendingDeleteRunnable = null;
              }
              pendingDeletePayload = null;
              SessionDataCache.clearAlbumPhotosPendingDeletion(
                  getAlbumName(), new ArrayList<>(pendingDeletePhotoNames));
              new Thread(
                      new SendMessagesThread("PHOTOS", MessageCodes.getRequest(), getAlbumName()))
                  .start();
            })
        .show();
  }

  private void sendPendingPhotoDeleteToServer() {
    if (pendingDeleteRunnable != null) {
      deleteHandler.removeCallbacks(pendingDeleteRunnable);
      pendingDeleteRunnable = null;
    }
    if (pendingDeletePayload == null
        || pendingDeletePayload.equals("")
        || pendingDeletePhotoNames.isEmpty()) {
      return;
    }
    new Thread(
            new SendMessagesThread("DEL_PHOTOS", MessageCodes.getRequest(), pendingDeletePayload))
        .start();
  }

  private Bitmap createLoadingBitmap() {
    Bitmap loadingBitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
    loadingBitmap.eraseColor(0xFFD8D8D8);
    return loadingBitmap;
  }

  private Bitmap createVideoPreviewBitmap() {
    return MediaTypeUtil.createVideoPlaceholder(320, 180);
  }

  private static final class VideoPreviewInfo {
    private final Bitmap previewBitmap;
    private final String durationLabel;

    private VideoPreviewInfo(Bitmap previewBitmap, String durationLabel) {
      this.previewBitmap = previewBitmap;
      this.durationLabel = durationLabel == null ? "" : durationLabel;
    }
  }

  private static final class ByteArrayMediaDataSource extends MediaDataSource {
    private final byte[] data;

    private ByteArrayMediaDataSource(byte[] data) {
      this.data = data;
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) {
      if (position >= data.length) {
        return -1;
      }
      int length = Math.min(size, data.length - (int) position);
      System.arraycopy(data, (int) position, buffer, offset, length);
      return length;
    }

    @Override
    public long getSize() {
      return data.length;
    }

    @Override
    public void close() {}
  }

  private VideoPreviewInfo extractVideoPreviewInfo(byte[] rawVideoBytes) {
    if (rawVideoBytes == null || rawVideoBytes.length == 0) {
      return new VideoPreviewInfo(createVideoPreviewBitmap(), "");
    }
    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
    try {
      retriever.setDataSource(new ByteArrayMediaDataSource(rawVideoBytes));
      Bitmap frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
      if (frame == null) {
        frame = retriever.getFrameAtTime();
      }
      String durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
      long durationLong = 0L;
      if (durationMs != null) {
        try {
          durationLong = Long.parseLong(durationMs);
        } catch (NumberFormatException ignored) {
        }
      }
      String durationLabel = MediaTypeUtil.formatDurationMillis(durationLong);
      Bitmap scaled = frame == null ? null : downscaleForUpload(frame, PREVIEW_MAX_DIMENSION);
      return new VideoPreviewInfo(
          scaled == null ? createVideoPreviewBitmap() : scaled, durationLabel);
    } catch (RuntimeException e) {
      return new VideoPreviewInfo(createVideoPreviewBitmap(), "");
    } finally {
      try {
        retriever.release();
      } catch (IOException ignored) {
      }
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  private VideoPreviewInfo decodeVideoPreviewInfoFromEncoded(String encodedPreview) {
    if (encodedPreview == null || encodedPreview.equals("")) {
      return new VideoPreviewInfo(createVideoPreviewBitmap(), "");
    }
    try {
      byte[] decodedBytes = Base64.getDecoder().decode(encodedPreview);
      return extractVideoPreviewInfo(decodedBytes);
    } catch (IllegalArgumentException ignored) {
      return new VideoPreviewInfo(createVideoPreviewBitmap(), "");
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  private void decodeAndApplyPreviewAsync(
      final int placeholderIndex, final String photoName, final String encodedPreview) {
    new Thread(
            () -> {
              final Bitmap decodedPreview = decodePreviewBitmap(encodedPreview);
              runOnUiThread(
                  () -> {
                    Bitmap effectivePreview = decodedPreview;
                    String durationLabel = "";
                    if (effectivePreview == null && MediaTypeUtil.isVideoFileName(photoName)) {
                      VideoPreviewInfo previewInfo = decodeVideoPreviewInfoFromEncoded(encodedPreview);
                      effectivePreview = previewInfo.previewBitmap;
                      durationLabel = previewInfo.durationLabel;
                    }
                    if (effectivePreview == null) {
                      if (placeholderIndex >= 0) {
                        clearPlaceholderAtIndex(placeholderIndex);
                      }
                      return;
                    }
                    if (SessionDataCache.isPhotoPendingDeletion(getAlbumName(), photoName)) {
                      if (placeholderIndex >= 0) {
                        clearPlaceholderAtIndex(placeholderIndex);
                      }
                      return;
                    }
                    previewBitmapsByName.put(photoName, effectivePreview);
                    if (MediaTypeUtil.isVideoFileName(photoName)) {
                      if (!durationLabel.equals("")) {
                        videoDurationByName.put(photoName, durationLabel);
                      }
                      SessionDataCache.putAlbumPhotoVideoDuration(
                          getAlbumName(), photoName, durationLabel);
                    }
                    if (placeholderIndex >= 0) {
                      replacePlaceholderAtIndex(placeholderIndex, photoName, effectivePreview);
                    } else if (!updatePhotoBitmapIfExists(photoName, effectivePreview)) {
                      appendPhotoToRows(photoName, effectivePreview);
                    }
                    SessionDataCache.putAlbumPhotoPreview(
                        getAlbumName(), photoName, effectivePreview);
                  });
            })
        .start();
  }

  private void prepareLoadingPlaceholders(int count) {
    photos.clear();
    Bitmap loadingBitmap = createLoadingBitmap();
    for (int i = 0; i < count; i++) {
      appendPhotoToRows("__loading__" + i, loadingBitmap);
    }
  }

  private int getRenderedPhotoCount() {
    int count = 0;
    for (PhotosItem row : photos) {
      count += countSlot(row.getFirstName(), row.getFirstPhotoIcon());
      count += countSlot(row.getSecondName(), row.getSecondPhotoIcon());
      count += countSlot(row.getThirdName(), row.getThirdPhotoIcon());
      count += countSlot(row.getFourtName(), row.getFourthPhotoIcon());
    }
    return count;
  }

  private int countSlot(String name, Bitmap bitmap) {
    if (bitmap == null || name == null || name.equals("") || name.startsWith("__loading__")) {
      return 0;
    }
    return 1;
  }

  private void appendLoadingPlaceholders(int count) {
    if (count <= 0) {
      return;
    }
    Bitmap loadingBitmap = createLoadingBitmap();
    for (int i = 0; i < count; i++) {
      appendPhotoToRows("__loading__pending__" + i + "_" + System.nanoTime(), loadingBitmap);
    }
  }

  private void replacePlaceholderAtIndex(int index, String photoName, Bitmap bitmap) {
    if (index < 0) {
      return;
    }

    int rowIndex = index / 4;
    int columnIndex = index % 4;
    while (photos.size() <= rowIndex) {
      photos.add(new PhotosItem("__loading__" + (photos.size() * 4), createLoadingBitmap()));
    }

    PhotosItem row = photos.get(rowIndex);
    if (columnIndex == 0) {
      row.setFirstName(photoName);
      row.setFirstPhotoIcon(bitmap);
    } else if (columnIndex == 1) {
      row.setSecondName(photoName);
      row.setSecondPhotoIcon(bitmap);
    } else if (columnIndex == 2) {
      row.setThirdName(photoName);
      row.setThirdPhotoIcon(bitmap);
    } else {
      row.setFourtName(photoName);
      row.setFourthPhotoIcon(bitmap);
    }
    adapter.notifyItemChanged(rowIndex);
  }

  private void clearPlaceholderAtIndex(int index) {
    if (index < 0) {
      return;
    }
    int rowIndex = index / 4;
    int columnIndex = index % 4;
    if (rowIndex < 0 || rowIndex >= photos.size()) {
      return;
    }

    PhotosItem row = photos.get(rowIndex);
    if (columnIndex == 0) {
      row.setFirstName("");
      row.setFirstPhotoIcon(null);
    } else if (columnIndex == 1) {
      row.setSecondName("");
      row.setSecondPhotoIcon(null);
    } else if (columnIndex == 2) {
      row.setThirdName("");
      row.setThirdPhotoIcon(null);
    } else {
      row.setFourtName("");
      row.setFourthPhotoIcon(null);
    }
    adapter.notifyItemChanged(rowIndex);
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
      addIfRemains(
          row.getFirstName(), row.getFirstPhotoIcon(), toDelete, remainingNames, remainingBitmaps);
      addIfRemains(
          row.getSecondName(),
          row.getSecondPhotoIcon(),
          toDelete,
          remainingNames,
          remainingBitmaps);
      addIfRemains(
          row.getThirdName(), row.getThirdPhotoIcon(), toDelete, remainingNames, remainingBitmaps);
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
                    firstName, firstBitmap, secondName, secondBitmap, thirdName, thirdBitmap));
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
      videoDurationByName.remove(deletedName);
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
    boolean wasClosed = MySocket.isClosed();
    MySocket.setClosed(false);
    if (wasClosed) {
      ReceiveMessagesThread.setActivity(this);
      ReceiveMessagesThread.setListener(SecondActivity.this);
      new Thread(new ReceiveMessagesThread()).start();
    }
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
      byte[] rawImageBytes = null;
      try {
        InputStream stream = getContentResolver().openInputStream(selectedImage);
        if (stream != null) {
          try {
            rawImageBytes = readAllBytes(stream);
          } finally {
            stream.close();
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
      if (rawImageBytes == null || rawImageBytes.length == 0) {
        Toast.makeText(
                getApplicationContext(), getString(R.string.upload_photo_error), Toast.LENGTH_SHORT)
            .show();
        return;
      }
      nameOfFile = ensureFileNameHasExtension(nameOfFile, selectedImage, rawImageBytes);

      Bitmap imageToPreview = BitmapFactory.decodeByteArray(rawImageBytes, 0, rawImageBytes.length);
      boolean isVideo = MediaTypeUtil.isVideoFileName(nameOfFile) || contentType.startsWith("video/");
      if (imageToPreview == null && !isVideo) {
        Toast.makeText(
                getApplicationContext(), getString(R.string.upload_photo_error), Toast.LENGTH_SHORT)
            .show();
        return;
      }

      String durationLabel = "";
      Bitmap localPreview;
      if (isVideo) {
        VideoPreviewInfo localVideoPreviewInfo = extractVideoPreviewInfo(rawImageBytes);
        localPreview = localVideoPreviewInfo.previewBitmap;
        durationLabel = localVideoPreviewInfo.durationLabel;
      } else {
        localPreview = downscaleForUpload(imageToPreview, PREVIEW_MAX_DIMENSION);
      }
      if (localPreview != null
          && getIntent().hasExtra("album_name")
          && !SessionDataCache.isPhotoPendingDeletion(getAlbumName(), nameOfFile)) {
        String albumName = getIntent().getExtras().getString("album_name");
        previewBitmapsByName.put(nameOfFile, localPreview);
        if (isVideo && !durationLabel.equals("")) {
          videoDurationByName.put(nameOfFile, durationLabel);
        }
        if (!updatePhotoBitmapIfExists(nameOfFile, localPreview)) {
          appendPhotoToRows(nameOfFile, localPreview);
        }
        SessionDataCache.putAlbumPhotoPreview(albumName, nameOfFile, localPreview);
        if (isVideo) {
          SessionDataCache.putAlbumPhotoVideoDuration(albumName, nameOfFile, durationLabel);
        }
      }

      String encodedImage =
          android.util.Base64.encodeToString(rawImageBytes, android.util.Base64.NO_WRAP);

      if (getIntent().hasExtra("album_name")) {
        MySocket.beginTransfer();
        TransferNotificationHelper.showUploadProgress(getApplicationContext(), 0, 0);
        acquireTransferWakeLock();
        sendUploadInChunks(
            getIntent().getExtras().getString("album_name"), nameOfFile, encodedImage);
      }
    }
  }

  private byte[] readAllBytes(InputStream stream) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    byte[] data = new byte[16384];
    int nRead;
    while ((nRead = stream.read(data, 0, data.length)) != -1) {
      buffer.write(data, 0, nRead);
    }
    return buffer.toByteArray();
  }

  @Override
  protected void onResume() {
    super.onResume();
    ReceiveMessagesThread.setActivity(this);
    ReceiveMessagesThread.setListener(SecondActivity.this);
    if (MySocket.isClosed()) {
      MySocket.setClosed(false);
    }
    new Thread(new ReceiveMessagesThread()).start();
  }

  @Override
  public void onBackPressed() {
    if (selectionMode) {
      selectionMode = false;
      selectedPhotoNames.clear();
      refreshSelectionUi();
      adapter.notifyDataSetChanged();
      return;
    }

    Intent goMain = new Intent(getApplicationContext(), MainActivity.class);
    if (getIntent().hasExtra("albums")) {
      goMain.putExtra("albums", getIntent().getExtras().getString("albums"));
    }
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

  private String ensureFileNameHasExtension(String fileName, android.net.Uri uri, byte[] rawBytes) {
    String safeName = fileName == null ? "" : fileName.trim();
    if (safeName.equals("")) {
      safeName = "media_" + System.currentTimeMillis();
    }
    int dotIndex = safeName.lastIndexOf('.');
    if (dotIndex > 0 && dotIndex < safeName.length() - 1) {
      return safeName;
    }

    String contentType = getContentResolver().getType(uri);
    if (contentType == null || contentType.trim().equals("")) {
      return safeName;
    }

    String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(contentType);
    if (extension == null || extension.trim().equals("")) {
      if (contentType.equalsIgnoreCase("image/gif")) {
        extension = "gif";
      } else if (contentType.equalsIgnoreCase("video/mp4")) {
        extension = "mp4";
      } else if (contentType.equalsIgnoreCase("video/webm")) {
        extension = "webm";
      } else if (contentType.equalsIgnoreCase("video/quicktime")) {
        extension = "mov";
      } else if (contentType.equalsIgnoreCase("video/x-matroska")) {
        extension = "mkv";
      } else if (contentType.equalsIgnoreCase("video/x-msvideo")) {
        extension = "avi";
      } else if (contentType.equalsIgnoreCase("video/3gpp")) {
        extension = "3gp";
      }
    }

    if (extension == null || extension.trim().equals("")) {
      if (rawBytes != null && rawBytes.length >= 6) {
        if (rawBytes[0] == 'G'
            && rawBytes[1] == 'I'
            && rawBytes[2] == 'F'
            && rawBytes[3] == '8'
            && (rawBytes[4] == '7' || rawBytes[4] == '9')
            && rawBytes[5] == 'a') {
          extension = "gif";
        }
      }
    }

    if (extension == null || extension.trim().equals("")) {
      return safeName;
    }
    return safeName + "." + extension.toLowerCase(Locale.ROOT);
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  @Override
  public void messageReceived(String mes, Activity activity) {
    HandelMessage message = new HandelMessage(mes);
    if (message.getName().equals("PHOTOS")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        generatePhotos(message.getData());
      } else if (message.getType().equals(MessageCodes.getPhotosError())) {
        dismissLoadingIndicator();
        Toast.makeText(
                getApplicationContext(),
                getResources().getString(R.string.photos_error),
                Toast.LENGTH_SHORT)
            .show();
      }
    }

    if (message.getName().equals("PHOTOS_COUNT")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        String[] lines = message.getData().split("\n");
        if (lines.length >= 2) {
          try {
            expectedPreviewCount = Integer.parseInt(lines[1]);
          } catch (NumberFormatException ignored) {
            expectedPreviewCount = 0;
          }
          incomingPreviewChunksByIndex.clear();
          incomingPreviewPartsByIndex.clear();
          incomingPreviewNameByIndex.clear();
          if (expectedPreviewCount > 0) {
            if (photos.isEmpty()) {
              previewPlaceholderOffset = 0;
              prepareLoadingPlaceholders(expectedPreviewCount);
            } else {
              previewPlaceholderOffset = getRenderedPhotoCount();
              appendLoadingPlaceholders(expectedPreviewCount);
            }
          } else {
            previewPlaceholderOffset = getRenderedPhotoCount();
          }
        }
      }
    }

    if (message.getName().equals("PHOTOS_CHUNK")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        String[] chunkLines = message.getData().split("\n", 7);
        if (chunkLines.length >= 7) {
          try {
            int photoIndex = Integer.parseInt(chunkLines[1]);
            String photoName = chunkLines[3];
            int partIndex = Integer.parseInt(chunkLines[4]);
            int partTotal = Integer.parseInt(chunkLines[5]);
            String chunkData = chunkLines[6];

            Map<Integer, String> chunksByPart = incomingPreviewChunksByIndex.get(photoIndex);
            if (chunksByPart == null) {
              chunksByPart = new HashMap<>();
              incomingPreviewChunksByIndex.put(photoIndex, chunksByPart);
            }
            incomingPreviewNameByIndex.put(photoIndex, photoName);
            incomingPreviewPartsByIndex.put(photoIndex, partTotal);
            chunksByPart.put(partIndex, chunkData);

            if (chunksByPart.size() >= partTotal) {
              StringBuilder assembled = new StringBuilder();
              boolean hasAllParts = true;
              for (int expectedPart = 0; expectedPart < partTotal; expectedPart++) {
                String chunkPart = chunksByPart.get(expectedPart);
                if (chunkPart == null) {
                  hasAllParts = false;
                  break;
                }
                assembled.append(chunkPart);
              }

              String assembledPreview = hasAllParts ? assembled.toString() : null;
              incomingPreviewChunksByIndex.remove(photoIndex);
              incomingPreviewPartsByIndex.remove(photoIndex);
              incomingPreviewNameByIndex.remove(photoIndex);

              if (assembledPreview == null) {
                clearPlaceholderAtIndex(previewPlaceholderOffset + photoIndex);
              } else {
                decodeAndApplyPreviewAsync(
                    previewPlaceholderOffset + photoIndex, photoName, assembledPreview);
              }
            }
          } catch (NumberFormatException ignored) {
          }
        }
      }
    }

    if (message.getName().equals("PHOTOS_DONE")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        dismissLoadingIndicator();
        for (Integer pendingIndex : new ArrayList<>(incomingPreviewChunksByIndex.keySet())) {
          clearPlaceholderAtIndex(previewPlaceholderOffset + pendingIndex);
        }
        incomingPreviewChunksByIndex.clear();
        incomingPreviewPartsByIndex.clear();
        incomingPreviewNameByIndex.clear();
        expectedPreviewCount = 0;
      }
    }

    if (message.getName().equals("UPLOAD_PHOTO")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        MySocket.endTransfer();
        TransferNotificationHelper.completeUpload(getApplicationContext());
        releaseTransferWakeLock();
        addPhoto(message.getData());
      } else if (message.getType().equals(MessageCodes.getUploadPhotoError())) {
        MySocket.endTransfer();
        TransferNotificationHelper.failUpload(getApplicationContext());
        releaseTransferWakeLock();
        Toast.makeText(
                getApplicationContext(),
                getResources().getString(R.string.upload_photo_error),
                Toast.LENGTH_SHORT)
            .show();
      }
    }
    if (message.getName().equals("DEL_PHOTOS")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        SessionDataCache.clearAlbumPhotosPendingDeletion(
            getAlbumName(), new ArrayList<>(pendingDeletePhotoNames));
        SessionDataCache.removeAlbumPhotoPreviews(
            getAlbumName(), new ArrayList<>(pendingDeletePhotoNames));
        pendingDeletePhotoNames.clear();
        pendingDeleteRunnable = null;
        pendingDeletePayload = null;
        Toast.makeText(
                getApplicationContext(), getString(R.string.photos_deleted), Toast.LENGTH_SHORT)
            .show();
      } else if (message.getType().equals(MessageCodes.getDelPhotosError())) {
        if (pendingDeleteRunnable != null) {
          deleteHandler.removeCallbacks(pendingDeleteRunnable);
          pendingDeleteRunnable = null;
        }
        SessionDataCache.clearAlbumPhotosPendingDeletion(
            getAlbumName(), new ArrayList<>(pendingDeletePhotoNames));
        new Thread(new SendMessagesThread("PHOTOS", MessageCodes.getRequest(), getAlbumName()))
            .start();
        pendingDeletePhotoNames.clear();
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
    if (pendingDeleteRunnable != null && !pendingDeletePhotoNames.isEmpty()) {
      deleteHandler.removeCallbacks(pendingDeleteRunnable);
      sendPendingPhotoDeleteToServer();
      pendingDeleteRunnable = null;
    }
  }

  @Override
  protected void onDestroy() {
    releaseTransferWakeLock();
    super.onDestroy();
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
