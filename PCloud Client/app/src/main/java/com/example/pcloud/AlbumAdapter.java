package com.example.pcloud;

import android.content.Context;
import android.content.Intent;
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
    private ArrayList<AlbumItem> albums;
    Context context;

    public AlbumAdapter(Context context, ArrayList<AlbumItem> albums) {
        this.context = context;
        this.albums = albums;
    }


    public class AlbumViewHolder extends RecyclerView.ViewHolder {
        ImageView albumImageView;
        TextView albumTextView;
        ConstraintLayout albumLayout;
        ;

        public AlbumViewHolder(@NonNull View itemView) {
            super(itemView);
            albumImageView = itemView.findViewById(R.id.albumAlbumLayoutImageView);
            albumTextView = itemView.findViewById(R.id.albumNameAlbumLayoutTextView);
            albumLayout = itemView.findViewById(R.id.albumLayout);
        }
    }

    @NonNull
    @Override
    public AlbumViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.album_layout, parent, false);
        return new AlbumViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlbumViewHolder holder, int position) {
        AlbumItem currentItem = albums.get(position);
        holder.albumImageView.setImageResource(R.drawable.album_default);
        holder.albumTextView.setText(currentItem.getAlbumName());

        holder.albumLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent goSecond = new Intent(context, SecondActivity.class);
                goSecond.putExtra("album_name",currentItem.getAlbumName());
                String albumsString = "";
                for (int i = 0; i < albums.size(); i++){
                    albumsString += albums.get(i).getAlbumName() + "\n";
                }
                goSecond.putExtra("albums", albumsString);
                MySocket.setClosed(true);
                goSecond.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(goSecond);
            }
        });
    }

    @Override
    public int getItemCount() {
        return albums.size();
    }
}
