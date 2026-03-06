package com.example.pcloud;

import android.graphics.Bitmap;

class PhotosItem {
  private String firstName;
  private Bitmap firstPhotoIcon;
  private String secondName;
  private Bitmap secondPhotoIcon;
  private String thirdName;
  private Bitmap thirdPhotoIcon;
  private String fourtName;
  private Bitmap fourthPhotoIcon;

  public PhotosItem(
      String firstName,
      Bitmap firstAlbumIcon,
      String secondName,
      Bitmap secondAlbumIcon,
      String thirdName,
      Bitmap thirdPhotoIcon,
      String fourtName,
      Bitmap fourthPhotoIcon) {
    this.firstName = firstName;
    this.firstPhotoIcon = firstAlbumIcon;
    this.secondName = secondName;
    this.secondPhotoIcon = secondAlbumIcon;
    this.thirdName = thirdName;
    this.thirdPhotoIcon = thirdPhotoIcon;
    this.fourtName = fourtName;
    this.fourthPhotoIcon = fourthPhotoIcon;
  }

  public PhotosItem(
      String firstName,
      Bitmap firstAlbumIcon,
      String secondName,
      Bitmap secondAlbumIcon,
      String thirdName,
      Bitmap thirdPhotoIcon) {
    this.firstName = firstName;
    this.firstPhotoIcon = firstAlbumIcon;
    this.secondName = secondName;
    this.secondPhotoIcon = secondAlbumIcon;
    this.thirdName = thirdName;
    this.thirdPhotoIcon = thirdPhotoIcon;
    this.fourtName = "";
    this.fourthPhotoIcon = null;
  }

  public PhotosItem(
      String firstName, Bitmap firstAlbumIcon, String secondName, Bitmap secondAlbumIcon) {
    this.firstName = firstName;
    this.firstPhotoIcon = firstAlbumIcon;
    this.secondName = secondName;
    this.secondPhotoIcon = secondAlbumIcon;
    this.thirdName = "";
    this.thirdPhotoIcon = null;
    this.fourtName = "";
    this.fourthPhotoIcon = null;
  }

  public PhotosItem(String firstName, Bitmap firstAlbumIcon) {
    this.firstName = firstName;
    this.firstPhotoIcon = firstAlbumIcon;
    this.secondName = "";
    this.secondPhotoIcon = null;
    this.thirdName = "";
    this.thirdPhotoIcon = null;
    this.fourtName = "";
    this.fourthPhotoIcon = null;
  }

  public Bitmap getFirstPhotoIcon() {
    return firstPhotoIcon;
  }

  public Bitmap getSecondPhotoIcon() {
    return secondPhotoIcon;
  }

  public Bitmap getThirdPhotoIcon() {
    return thirdPhotoIcon;
  }

  public Bitmap getFourthPhotoIcon() {
    return fourthPhotoIcon;
  }

  public void setFirstPhotoIcon(Bitmap firstPhotoIcon) {
    this.firstPhotoIcon = firstPhotoIcon;
  }

  public void setSecondPhotoIcon(Bitmap secondPhotoIcon) {
    this.secondPhotoIcon = secondPhotoIcon;
  }

  public void setThirdPhotoIcon(Bitmap thirdPhotoIcon) {
    this.thirdPhotoIcon = thirdPhotoIcon;
  }

  public void setFourthPhotoIcon(Bitmap fourthPhotoIcon) {
    this.fourthPhotoIcon = fourthPhotoIcon;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getSecondName() {
    return secondName;
  }

  public void setSecondName(String secondName) {
    this.secondName = secondName;
  }

  public String getThirdName() {
    return thirdName;
  }

  public void setThirdName(String thirdName) {
    this.thirdName = thirdName;
  }

  public String getFourtName() {
    return fourtName;
  }

  public void setFourtName(String fourtName) {
    this.fourtName = fourtName;
  }
}
