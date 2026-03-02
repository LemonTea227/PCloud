package com.example.pcloud;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

class PhotosAdapter extends RecyclerView.Adapter<PhotosAdapter.PhotosViewHolder> {
    private ArrayList<PhotosItem> photos;
    Context context;
    String albums;
    String album_name;

    public PhotosAdapter(Context context, ArrayList<PhotosItem> photos, String albums, String album_name) {
        this.context = context;
        this.photos = photos;
        this.albums = albums;
        this.album_name = album_name;
    }

    public PhotosAdapter(Context context, ArrayList<PhotosItem> photos) {
        this.context = context;
        this.photos = photos;
        this.albums = "";
        this.album_name = "";
    }

    public class PhotosViewHolder extends RecyclerView.ViewHolder{
        public ImageButton firstPhotoImageButton;
        public ImageButton secondPhotoImageButton;
        public ImageButton thirdPhotoImageButton;
        public ImageButton fourthPhotoImageButton;


        public PhotosViewHolder(@NonNull View itemView) {
            super(itemView);
            this.firstPhotoImageButton = itemView.findViewById(R.id.firstPhotoImageButton);
            this.secondPhotoImageButton = itemView.findViewById(R.id.secondPhotoImageButton);
            this.thirdPhotoImageButton = itemView.findViewById(R.id.thirdPhotoImageButton);
            this.fourthPhotoImageButton = itemView.findViewById(R.id.fourthPhotoImageButton);
        }


    }

    @NonNull
    @Override
    public PhotosViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.photos_layout, parent, false);
        return new PhotosViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotosViewHolder holder, int position) {
        PhotosItem currentItem = photos.get(position);
        holder.firstPhotoImageButton.setImageBitmap(currentItem.getFirstPhotoIcon());
        if (currentItem.getFourthPhotoIcon() != null) {
            holder.fourthPhotoImageButton.setImageBitmap(currentItem.getFourthPhotoIcon());
            holder.fourthPhotoImageButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent goPhotoViewer = new Intent(context, PhotoViewerActivity.class);
                    goPhotoViewer.putExtra("album_name",album_name);
                    goPhotoViewer.putExtra("albums", albums);
                    goPhotoViewer.putExtra("photo_name", currentItem.getFourtName());
                    MySocket.setClosed(true);
                    goPhotoViewer.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(goPhotoViewer);
                }
            });
        }
        if (currentItem.getThirdPhotoIcon() != null) {
            holder.thirdPhotoImageButton.setImageBitmap(currentItem.getThirdPhotoIcon());
            holder.thirdPhotoImageButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent goPhotoViewer = new Intent(context, PhotoViewerActivity.class);
                    goPhotoViewer.putExtra("album_name",album_name);
                    goPhotoViewer.putExtra("albums", albums);
                    goPhotoViewer.putExtra("photo_name", currentItem.getThirdName());
                    MySocket.setClosed(true);
                    goPhotoViewer.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(goPhotoViewer);
                }
            });
        }
        if (currentItem.getSecondPhotoIcon() != null){
            holder.secondPhotoImageButton.setImageBitmap(currentItem.getSecondPhotoIcon());

            holder.secondPhotoImageButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent goPhotoViewer = new Intent(context, PhotoViewerActivity.class);
                    goPhotoViewer.putExtra("album_name",album_name);
                    goPhotoViewer.putExtra("albums", albums);
                    goPhotoViewer.putExtra("photo_name", currentItem.getSecondName());
                    MySocket.setClosed(true);
                    goPhotoViewer.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(goPhotoViewer);
                }
            });
        }
        holder.firstPhotoImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent goPhotoViewer = new Intent(context, PhotoViewerActivity.class);
                goPhotoViewer.putExtra("album_name",album_name);
                goPhotoViewer.putExtra("albums", albums);
                goPhotoViewer.putExtra("photo_name", currentItem.getFirstName());
                MySocket.setClosed(true);
                goPhotoViewer.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(goPhotoViewer);
            }
        });
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }
}
