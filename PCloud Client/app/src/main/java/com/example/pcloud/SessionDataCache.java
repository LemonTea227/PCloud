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

  private SessionDataCache() {}

  public static synchronized void clearAll() {
    albumNames.clear();
    albumPreviewBitmaps.clear();
    pendingDeletedPhotoNamesByAlbum.clear();
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
  }

  public static synchronized void clearAlbumPreviewCache(String albumName) {
    if (albumName == null) {
      return;
    }
    albumPreviewBitmaps.remove(albumName.trim());
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
    LinkedHashMap<String, Bitmap> cached = albumPreviewBitmaps.get(albumName.trim());
    if (cached == null) {
      return;
    }
    for (String photoName : photoNames) {
      if (photoName != null) {
        cached.remove(photoName.trim());
      }
    }
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
