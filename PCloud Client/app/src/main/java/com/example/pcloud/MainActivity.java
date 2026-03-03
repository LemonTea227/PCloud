package com.example.pcloud;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import androidx.appcompat.widget.SearchView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements ReceiveMessagesListener {
  private RecyclerView albumMainRecyclerView;
  private RecyclerView.Adapter adapter;
  private RecyclerView.LayoutManager layoutManager;

  ArrayList<AlbumItem> albums;
  ArrayList<AlbumItem> allAlbums;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    ReceiveMessagesThread.setActivity(this);
    ReceiveMessagesThread.setListener(MainActivity.this);

    new Thread(new ReceiveMessagesThread()).start();

    if (getIntent().hasExtra("albums")) {
      generateAlbums(getIntent().getExtras().getString("albums"));
    } else {
      generateAlbums("");
    }

    albumMainRecyclerView = findViewById(R.id.albumMainRecyclerView);

    albumMainRecyclerView.setHasFixedSize(true);
    layoutManager = new LinearLayoutManager(this);
    adapter = new AlbumAdapter(getApplicationContext(), albums);

    albumMainRecyclerView.setLayoutManager(layoutManager);
    albumMainRecyclerView.setAdapter(adapter);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.albums_menu, menu);

    MenuItem searchItem = menu.findItem(R.id.searchAlbumsMenuItem);
    SearchView searchView = (SearchView) searchItem.getActionView();
    searchView.setQueryHint(getString(R.string.search_albums));
    searchView.setOnQueryTextListener(
        new SearchView.OnQueryTextListener() {
          @Override
          public boolean onQueryTextSubmit(String query) {
            filterAlbums(query);
            return true;
          }

          @Override
          public boolean onQueryTextChange(String newText) {
            filterAlbums(newText);
            return true;
          }
        });

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    switch (item.getItemId()) {
      case R.id.addAlbumsMenuItem:
        Intent goCreateAlbum = new Intent(getApplicationContext(), CreateAlbumActivity.class);
        if (getIntent().hasExtra("albums")) {
          goCreateAlbum.putExtra("albums", getIntent().getExtras().getString("albums"));
        }
        MySocket.setClosed(true);
        startActivity(goCreateAlbum);
        return true;
      case R.id.settingsAlbumsMenuItem:
        Intent goSettings = new Intent(getApplicationContext(), SettingsActivity.class);
        if (getIntent().hasExtra("albums")) {
          goSettings.putExtra("albums", getIntent().getExtras().getString("albums"));
        }
        MySocket.setClosed(true);
        startActivity(goSettings);
        return true;
      case R.id.aboutAlbumsMenuItem:
        Intent goAbout = new Intent(getApplicationContext(), AboutActivity.class);
        if (getIntent().hasExtra("albums")) {
          goAbout.putExtra("albums", getIntent().getExtras().getString("albums"));
        }
        MySocket.setClosed(true);
        startActivity(goAbout);
        return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void generateAlbums(String message) {
    allAlbums = new ArrayList<AlbumItem>();
    if (!message.equals("")) {
      String[] albums_message = message.split("\n");
      for (int i = 0; i < albums_message.length; i++) {
        allAlbums.add(new AlbumItem(albums_message[i]));
      }
    }
    albums = new ArrayList<AlbumItem>(allAlbums);
  }

  private void filterAlbums(String query) {
    String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    albums.clear();
    if (normalized.isEmpty()) {
      albums.addAll(allAlbums);
    } else {
      for (AlbumItem album : allAlbums) {
        if (album.getAlbumName().toLowerCase(Locale.ROOT).contains(normalized)) {
          albums.add(album);
        }
      }
    }
    adapter.notifyDataSetChanged();
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
    //                Toast.makeText(getApplicationContext(),
    // getResources().getString(R.string.photos_error), Toast.LENGTH_SHORT).show();
    //            }
    //        }
  }
}
