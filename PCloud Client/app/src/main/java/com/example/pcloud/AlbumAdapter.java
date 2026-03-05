package com.example.pcloud;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

public class AlbumAdapter extends RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder> {
  interface AlbumInteractionListener {
    void onOpenAlbum(String albumName);

    void onToggleAlbumSelection(String albumName);

    boolean isSelectionModeEnabled();

    boolean isAlbumSelected(String albumName);
  }

  private ArrayList<AlbumItem> albums;
  Context context;
  private AlbumInteractionListener interactionListener;

  public AlbumAdapter(Context context, ArrayList<AlbumItem> albums) {
    this.context = context;
    this.albums = albums;
  }

  public AlbumAdapter(
      Context context, ArrayList<AlbumItem> albums, AlbumInteractionListener interactionListener) {
    this(context, albums);
    this.interactionListener = interactionListener;
  }

  public class AlbumViewHolder extends RecyclerView.ViewHolder {
    ImageView albumImageView;
    ImageView albumSelectedCheckImageView;
    TextView albumTextView;
    ConstraintLayout albumLayout;

    public AlbumViewHolder(@NonNull View itemView) {
      super(itemView);
      albumImageView = itemView.findViewById(R.id.albumAlbumLayoutImageView);
      albumSelectedCheckImageView = itemView.findViewById(R.id.albumSelectedCheckImageView);
      albumTextView = itemView.findViewById(R.id.albumNameAlbumLayoutTextView);
      albumLayout = itemView.findViewById(R.id.albumLayout);
    }
  }

  @NonNull
  @Override
  public AlbumViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view =
        LayoutInflater.from(parent.getContext()).inflate(R.layout.album_layout, parent, false);
    return new AlbumViewHolder(view);
  }

  @Override
  public void onBindViewHolder(@NonNull AlbumViewHolder holder, int position) {
    AlbumItem currentItem = albums.get(position);
    holder.albumImageView.setImageResource(R.drawable.album_default);
    holder.albumTextView.setText(currentItem.getAlbumName());

    boolean selected =
        interactionListener != null
            && interactionListener.isAlbumSelected(currentItem.getAlbumName());
    holder.albumSelectedCheckImageView.setVisibility(selected ? View.VISIBLE : View.GONE);
    holder.albumLayout.setAlpha(selected ? 0.65f : 1.0f);

    holder.albumLayout.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            if (interactionListener == null) {
              return;
            }
            if (interactionListener.isSelectionModeEnabled()) {
              interactionListener.onToggleAlbumSelection(currentItem.getAlbumName());
            } else {
              interactionListener.onOpenAlbum(currentItem.getAlbumName());
            }
          }
        });

    holder.albumLayout.setOnLongClickListener(
        v -> {
          if (interactionListener != null) {
            interactionListener.onToggleAlbumSelection(currentItem.getAlbumName());
            return true;
          }
          return false;
        });
  }

  @Override
  public int getItemCount() {
    return albums.size();
  }
}
