package com.example.pcloud;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

class PhotosAdapter extends RecyclerView.Adapter<PhotosAdapter.PhotosViewHolder> {
  interface PhotoInteractionListener {
    void onOpenPhoto(String photoName);

    void onTogglePhotoSelection(String photoName);

    boolean isSelectionModeEnabled();

    boolean isPhotoSelected(String photoName);

    String getVideoDurationLabel(String photoName);
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
    public ImageView firstPhotoVideoIndicator;
    public ImageView secondPhotoVideoIndicator;
    public ImageView thirdPhotoVideoIndicator;
    public ImageView fourthPhotoVideoIndicator;
    public TextView firstPhotoVideoDuration;
    public TextView secondPhotoVideoDuration;
    public TextView thirdPhotoVideoDuration;
    public TextView fourthPhotoVideoDuration;

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
      this.firstPhotoVideoIndicator = itemView.findViewById(R.id.firstPhotoVideoIndicator);
      this.secondPhotoVideoIndicator = itemView.findViewById(R.id.secondPhotoVideoIndicator);
      this.thirdPhotoVideoIndicator = itemView.findViewById(R.id.thirdPhotoVideoIndicator);
      this.fourthPhotoVideoIndicator = itemView.findViewById(R.id.fourthPhotoVideoIndicator);
      this.firstPhotoVideoDuration = itemView.findViewById(R.id.firstPhotoVideoDuration);
      this.secondPhotoVideoDuration = itemView.findViewById(R.id.secondPhotoVideoDuration);
      this.thirdPhotoVideoDuration = itemView.findViewById(R.id.thirdPhotoVideoDuration);
      this.fourthPhotoVideoDuration = itemView.findViewById(R.id.fourthPhotoVideoDuration);
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
        holder.firstPhotoVideoIndicator,
        holder.firstPhotoVideoDuration,
        currentItem.getFirstName(),
        currentItem.getFirstPhotoIcon());
    bindPhotoButton(
        holder.secondPhotoImageButton,
        holder.secondPhotoSelectedIndicator,
        holder.secondPhotoVideoIndicator,
        holder.secondPhotoVideoDuration,
        currentItem.getSecondName(),
        currentItem.getSecondPhotoIcon());
    bindPhotoButton(
        holder.thirdPhotoImageButton,
        holder.thirdPhotoSelectedIndicator,
        holder.thirdPhotoVideoIndicator,
        holder.thirdPhotoVideoDuration,
        currentItem.getThirdName(),
        currentItem.getThirdPhotoIcon());
    bindPhotoButton(
        holder.fourthPhotoImageButton,
        holder.fourthPhotoSelectedIndicator,
        holder.fourthPhotoVideoIndicator,
        holder.fourthPhotoVideoDuration,
        currentItem.getFourtName(),
        currentItem.getFourthPhotoIcon());
  }

  private void bindPhotoButton(
      ImageButton button,
      ImageView indicator,
      ImageView videoIndicator,
      TextView videoDuration,
      String photoName,
      android.graphics.Bitmap bitmap) {
    if (bitmap == null || photoName == null || photoName.equals("")) {
      button.setImageDrawable(null);
      button.setVisibility(View.INVISIBLE);
      button.setOnClickListener(null);
      button.setOnLongClickListener(null);
      indicator.setVisibility(View.GONE);
      videoIndicator.setVisibility(View.GONE);
      videoDuration.setVisibility(View.GONE);
      return;
    }

    boolean isLoadingPlaceholder = photoName.startsWith("__loading__");

    button.setVisibility(View.VISIBLE);
    button.setImageBitmap(bitmap);
    boolean isVideo = MediaTypeUtil.isVideoFileName(photoName);
    boolean isGif = MediaTypeUtil.isGifFileName(photoName);
    videoIndicator.setVisibility(isVideo ? View.VISIBLE : View.GONE);
    String durationLabel =
        interactionListener == null ? "" : interactionListener.getVideoDurationLabel(photoName);
    if (isVideo && durationLabel != null && !durationLabel.trim().equals("")) {
      videoDuration.setText(durationLabel);
      videoDuration.setVisibility(View.VISIBLE);
    } else if (isGif) {
      videoDuration.setText("GIF");
      videoDuration.setVisibility(View.VISIBLE);
    } else {
      videoDuration.setVisibility(View.GONE);
    }
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
