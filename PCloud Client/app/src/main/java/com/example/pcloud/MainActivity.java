package com.example.pcloud;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements ReceiveMessagesListener {
    private RecyclerView albumMainRecyclerView;
    private RecyclerView.Adapter adapter;
    private RecyclerView.LayoutManager layoutManager;

    ArrayList<AlbumItem> albums;

    boolean first = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ReceiveMessagesThread.setActivity(this);
        ReceiveMessagesThread.setListener(MainActivity.this);

        new Thread(new ReceiveMessagesThread()).start();

        if (getIntent().hasExtra("albums")){
            generateAlbums(getIntent().getExtras().getString("albums"));
        }
        else {
            generateAlbums("");
        }


        albumMainRecyclerView = findViewById(R.id.albumMainRecyclerView);

        albumMainRecyclerView.setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(this);
        adapter = new AlbumAdapter(getApplicationContext(), this.albums);

        albumMainRecyclerView.setLayoutManager(layoutManager);
        albumMainRecyclerView.setAdapter(adapter);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (first) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.albums_menu, menu);
            first = false;
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.addAlbumsMenuItem:
                Intent goCreateAlbum = new Intent(getApplicationContext(), CreateAlbumActivity.class);
                if (getIntent().hasExtra("albums")){
                    goCreateAlbum.putExtra("albums", getIntent().getExtras().getString("albums"));
                }
                MySocket.setClosed(true);
                startActivity(goCreateAlbum);
                return true;
            case R.id.settingsAlbumsMenuItem:
                Intent goSettings = new Intent(getApplicationContext(), SettingsActivity.class);
                if (getIntent().hasExtra("albums")){
                    goSettings.putExtra("albums", getIntent().getExtras().getString("albums"));
                }
                MySocket.setClosed(true);
                startActivity(goSettings);
                return true;
            case R.id.aboutAlbumsMenuItem:
                Intent goAbout = new Intent(getApplicationContext(), AboutActivity.class);
                if (getIntent().hasExtra("albums")){
                    goAbout.putExtra("albums", getIntent().getExtras().getString("albums"));
                }
                MySocket.setClosed(true);
                startActivity(goAbout);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    private void generateAlbums(String message) {
        albums = new ArrayList<AlbumItem>();
        if (!message.equals("")) {
            String[] albums_message = message.split("\n");
            for (int i = 0; i < albums_message.length; i++) {
                albums.add(new AlbumItem(albums_message[i]));
            }
        }
    }

    @Override
    public void onBackPressed() {
//        finish();
//        System.exit(0);
    }

    @Override
    public void messageReceived(String mes, Activity activity) {
//        HandelMessage message = new HandelMessage(mes);
//        if (message.getName().equals("PHOTOS")) {
//            if (message.getType().equals(MessageCodes.getConfirm())) {
//                Intent goSecond = new Intent(getApplicationContext(), SecondActivity.class);
//                goSecond.putExtra("photos", message.getData());
//                if (getIntent().hasExtra("albums")) {
//                    goSecond.putExtra("albums", getIntent().getExtras().getString("albums"));
//                }
//                MySocket.setClosed(true);
//                startActivity(goSecond);
//            } else if (message.getType().equals(MessageCodes.getAlbumsError())) {
//                Toast.makeText(getApplicationContext(), getResources().getString(R.string.photos_error), Toast.LENGTH_SHORT).show();
//            }
//        }
    }
}