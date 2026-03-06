package com.example.pcloud;

import android.media.Image;

public class AlbumItem {
  private Image albumIcon;
  private String albumName;

  public AlbumItem(String AlbumName) {
    this.albumName = AlbumName;
  }

  public AlbumItem(Image AlbumIcon, String AlbumName) {
    this.albumIcon = AlbumIcon;
    this.albumName = AlbumName;
  }

  public Image getAlbumIcon() {
    return albumIcon;
  }

  public String getAlbumName() {
    return albumName;
  }
}
