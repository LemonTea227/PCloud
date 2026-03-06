package com.example.pcloud;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class SessionDataCache {
  private static final LinkedHashSet<String> albumNames = new LinkedHashSet<>();
  private static final Map<String, LinkedHashMap<String, Bitmap>> albumPreviewBitmaps =
      new LinkedHashMap<>();
  private static final Map<String, LinkedHashSet<String>> pendingDeletedPhotoNamesByAlbum =
      new LinkedHashMap<>();
    private static final Map<String, LinkedHashMap<String, String>> albumVideoDurations =
      new LinkedHashMap<>();

  private SessionDataCache() {}

  public static synchronized void clearAll() {
    albumNames.clear();
    albumPreviewBitmaps.clear();
    pendingDeletedPhotoNamesByAlbum.clear();
    albumVideoDurations.clear();
  }

  public static synchronized List<String> getAlbumNames() {
    return new ArrayList<>(albumNames);
  }

  public static synchronized void setAlbumNames(List<String> names) {
    albumNames.clear();
    if (names == null) {
      return;
    }
    for (String name : names) {
      if (name != null && !name.trim().isEmpty()) {
        albumNames.add(name.trim());
      }
    }
  }

  public static synchronized void addAlbumName(String albumName) {
    if (albumName == null || albumName.trim().isEmpty()) {
      return;
    }
    albumNames.add(albumName.trim());
  }

  public static synchronized void removeAlbumName(String albumName) {
    if (albumName == null) {
      return;
    }
    String normalized = albumName.trim();
    albumNames.remove(normalized);
    albumPreviewBitmaps.remove(normalized);
    pendingDeletedPhotoNamesByAlbum.remove(normalized);
    albumVideoDurations.remove(normalized);
  }

  public static synchronized void clearAlbumPreviewCache(String albumName) {
    if (albumName == null) {
      return;
    }
    String normalizedAlbum = albumName.trim();
    albumPreviewBitmaps.remove(normalizedAlbum);
    albumVideoDurations.remove(normalizedAlbum);
  }

  public static synchronized LinkedHashMap<String, Bitmap> getAlbumPreviewBitmaps(
      String albumName) {
    LinkedHashMap<String, Bitmap> cached = albumPreviewBitmaps.get(albumName);
    if (cached == null) {
      return new LinkedHashMap<>();
    }
    return new LinkedHashMap<>(cached);
  }

  public static synchronized List<String> getAlbumPhotoNames(String albumName) {
    LinkedHashMap<String, Bitmap> cached = albumPreviewBitmaps.get(albumName);
    if (cached == null) {
      return new ArrayList<>();
    }
    return new ArrayList<>(cached.keySet());
  }

  public static synchronized void putAlbumPhotoPreview(
      String albumName, String photoName, Bitmap previewBitmap) {
    if (albumName == null
        || albumName.trim().isEmpty()
        || photoName == null
        || photoName.trim().isEmpty()
        || previewBitmap == null) {
      return;
    }
    String normalizedAlbum = albumName.trim();
    String normalizedPhoto = photoName.trim();
    LinkedHashMap<String, Bitmap> cached = albumPreviewBitmaps.get(normalizedAlbum);
    if (cached == null) {
      cached = new LinkedHashMap<>();
      albumPreviewBitmaps.put(normalizedAlbum, cached);
    }
    cached.put(normalizedPhoto, previewBitmap);
  }

  public static synchronized void removeAlbumPhotoPreviews(
      String albumName, List<String> photoNames) {
    if (albumName == null || photoNames == null || photoNames.isEmpty()) {
      return;
    }
    String normalizedAlbum = albumName.trim();
    LinkedHashMap<String, Bitmap> cached = albumPreviewBitmaps.get(normalizedAlbum);
    LinkedHashMap<String, String> durations = albumVideoDurations.get(normalizedAlbum);
    for (String photoName : photoNames) {
      if (photoName != null) {
        if (cached != null) {
          cached.remove(photoName.trim());
        }
        if (durations != null) {
          durations.remove(photoName.trim());
        }
      }
    }
  }

  public static synchronized void putAlbumPhotoVideoDuration(
      String albumName, String photoName, String durationLabel) {
    if (albumName == null
        || albumName.trim().isEmpty()
        || photoName == null
        || photoName.trim().isEmpty()) {
      return;
    }
    String normalizedAlbum = albumName.trim();
    String normalizedPhoto = photoName.trim();
    LinkedHashMap<String, String> durations = albumVideoDurations.get(normalizedAlbum);
    if (durations == null) {
      durations = new LinkedHashMap<>();
      albumVideoDurations.put(normalizedAlbum, durations);
    }
    if (durationLabel == null || durationLabel.trim().isEmpty()) {
      durations.remove(normalizedPhoto);
      return;
    }
    durations.put(normalizedPhoto, durationLabel.trim());
  }

  public static synchronized String getAlbumPhotoVideoDuration(String albumName, String photoName) {
    if (albumName == null || photoName == null) {
      return "";
    }
    LinkedHashMap<String, String> durations = albumVideoDurations.get(albumName.trim());
    if (durations == null) {
      return "";
    }
    String value = durations.get(photoName.trim());
    return value == null ? "" : value;
  }

  public static synchronized void markAlbumPhotosPendingDeletion(
      String albumName, List<String> photoNames) {
    if (albumName == null
        || albumName.trim().isEmpty()
        || photoNames == null
        || photoNames.isEmpty()) {
      return;
    }
    String normalizedAlbum = albumName.trim();
    LinkedHashSet<String> pending = pendingDeletedPhotoNamesByAlbum.get(normalizedAlbum);
    if (pending == null) {
      pending = new LinkedHashSet<>();
      pendingDeletedPhotoNamesByAlbum.put(normalizedAlbum, pending);
    }
    for (String photoName : photoNames) {
      if (photoName != null && !photoName.trim().isEmpty()) {
        pending.add(photoName.trim());
      }
    }
  }

  public static synchronized void clearAlbumPhotosPendingDeletion(
      String albumName, List<String> photoNames) {
    if (albumName == null || albumName.trim().isEmpty()) {
      return;
    }
    String normalizedAlbum = albumName.trim();
    LinkedHashSet<String> pending = pendingDeletedPhotoNamesByAlbum.get(normalizedAlbum);
    if (pending == null) {
      return;
    }
    if (photoNames == null || photoNames.isEmpty()) {
      pendingDeletedPhotoNamesByAlbum.remove(normalizedAlbum);
      return;
    }
    for (String photoName : photoNames) {
      if (photoName != null) {
        pending.remove(photoName.trim());
      }
    }
    if (pending.isEmpty()) {
      pendingDeletedPhotoNamesByAlbum.remove(normalizedAlbum);
    }
  }

  public static synchronized boolean isPhotoPendingDeletion(String albumName, String photoName) {
    if (albumName == null || photoName == null) {
      return false;
    }
    LinkedHashSet<String> pending = pendingDeletedPhotoNamesByAlbum.get(albumName.trim());
    if (pending == null) {
      return false;
    }
    return pending.contains(photoName.trim());
  }
}
