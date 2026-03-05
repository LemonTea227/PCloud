package com.example.pcloud;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

class PhotosAdapter extends RecyclerView.Adapter<PhotosAdapter.PhotosViewHolder> {
  interface PhotoInteractionListener {
    void onOpenPhoto(String photoName);

    void onTogglePhotoSelection(String photoName);

    boolean isSelectionModeEnabled();

    boolean isPhotoSelected(String photoName);
  }

  private ArrayList<PhotosItem> photos;
  Context context;
  String albums;
  String album_name;
  private PhotoInteractionListener interactionListener;

  public PhotosAdapter(
      Context context, ArrayList<PhotosItem> photos, String albums, String album_name) {
    this.context = context;
    this.photos = photos;
    this.albums = albums;
    this.album_name = album_name;
  }

  public PhotosAdapter(
      Context context,
      ArrayList<PhotosItem> photos,
      String albums,
      String album_name,
      PhotoInteractionListener interactionListener) {
    this(context, photos, albums, album_name);
    this.interactionListener = interactionListener;
  }

  public PhotosAdapter(Context context, ArrayList<PhotosItem> photos) {
    this.context = context;
    this.photos = photos;
    this.albums = "";
    this.album_name = "";
  }

  public class PhotosViewHolder extends RecyclerView.ViewHolder {
    public ImageButton firstPhotoImageButton;
    public ImageButton secondPhotoImageButton;
    public ImageButton thirdPhotoImageButton;
    public ImageButton fourthPhotoImageButton;
    public ImageView firstPhotoSelectedIndicator;
    public ImageView secondPhotoSelectedIndicator;
    public ImageView thirdPhotoSelectedIndicator;
    public ImageView fourthPhotoSelectedIndicator;

    public PhotosViewHolder(@NonNull View itemView) {
      super(itemView);
      this.firstPhotoImageButton = itemView.findViewById(R.id.firstPhotoImageButton);
      this.secondPhotoImageButton = itemView.findViewById(R.id.secondPhotoImageButton);
      this.thirdPhotoImageButton = itemView.findViewById(R.id.thirdPhotoImageButton);
      this.fourthPhotoImageButton = itemView.findViewById(R.id.fourthPhotoImageButton);
      this.firstPhotoSelectedIndicator = itemView.findViewById(R.id.firstPhotoSelectedIndicator);
      this.secondPhotoSelectedIndicator = itemView.findViewById(R.id.secondPhotoSelectedIndicator);
      this.thirdPhotoSelectedIndicator = itemView.findViewById(R.id.thirdPhotoSelectedIndicator);
      this.fourthPhotoSelectedIndicator = itemView.findViewById(R.id.fourthPhotoSelectedIndicator);
    }
  }

  @NonNull
  @Override
  public PhotosViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view =
        LayoutInflater.from(parent.getContext()).inflate(R.layout.photos_layout, parent, false);
    return new PhotosViewHolder(view);
  }

  @Override
  public void onBindViewHolder(@NonNull PhotosViewHolder holder, int position) {
    PhotosItem currentItem = photos.get(position);
    bindPhotoButton(
        holder.firstPhotoImageButton,
        holder.firstPhotoSelectedIndicator,
        currentItem.getFirstName(),
        currentItem.getFirstPhotoIcon());
    bindPhotoButton(
        holder.secondPhotoImageButton,
        holder.secondPhotoSelectedIndicator,
        currentItem.getSecondName(),
        currentItem.getSecondPhotoIcon());
    bindPhotoButton(
        holder.thirdPhotoImageButton,
        holder.thirdPhotoSelectedIndicator,
        currentItem.getThirdName(),
        currentItem.getThirdPhotoIcon());
    bindPhotoButton(
        holder.fourthPhotoImageButton,
        holder.fourthPhotoSelectedIndicator,
        currentItem.getFourtName(),
        currentItem.getFourthPhotoIcon());
  }

  private void bindPhotoButton(
      ImageButton button, ImageView indicator, String photoName, android.graphics.Bitmap bitmap) {
    if (bitmap == null || photoName == null || photoName.equals("")) {
      button.setImageDrawable(null);
      button.setVisibility(View.INVISIBLE);
      button.setOnClickListener(null);
      button.setOnLongClickListener(null);
      indicator.setVisibility(View.GONE);
      return;
    }

    boolean isLoadingPlaceholder = photoName.startsWith("__loading__");

    button.setVisibility(View.VISIBLE);
    button.setImageBitmap(bitmap);
    boolean selected =
        interactionListener != null && interactionListener.isPhotoSelected(photoName);
    button.setAlpha(selected ? 0.55f : (isLoadingPlaceholder ? 0.35f : 1.0f));
    indicator.setVisibility(selected ? View.VISIBLE : View.GONE);

    if (isLoadingPlaceholder) {
      button.setOnClickListener(null);
      button.setOnLongClickListener(null);
      return;
    }

    button.setOnClickListener(
        v -> {
          if (interactionListener == null) {
            return;
          }
          if (interactionListener.isSelectionModeEnabled()) {
            interactionListener.onTogglePhotoSelection(photoName);
          } else {
            interactionListener.onOpenPhoto(photoName);
          }
        });
    button.setOnLongClickListener(
        v -> {
          if (interactionListener != null) {
            interactionListener.onTogglePhotoSelection(photoName);
            return true;
          }
          return false;
        });
  }

  @Override
  public int getItemCount() {
    return photos.size();
  }
}
