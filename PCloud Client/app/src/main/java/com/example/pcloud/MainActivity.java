package com.example.pcloud;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity
    implements ReceiveMessagesListener, AlbumAdapter.AlbumInteractionListener {
  private RecyclerView albumMainRecyclerView;
  private RecyclerView.Adapter adapter;
  private RecyclerView.LayoutManager layoutManager;
  private Menu optionsMenu;

  private ArrayList<AlbumItem> albums;
  private ArrayList<AlbumItem> allAlbums;
  private final LinkedHashSet<String> selectedAlbumNames = new LinkedHashSet<>();
  private boolean selectionMode;
  private String activeQuery = "";

  private final Handler deleteHandler = new Handler(Looper.getMainLooper());
  private Runnable pendingDeleteRunnable;
  private final ArrayList<AlbumItem> pendingDeletedAlbums = new ArrayList<>();
  private final ArrayList<String> pendingDeleteAlbumNames = new ArrayList<>();
  private boolean awaitingAlbumEmptyCheck;
  private String albumToCheckForEmpty;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    ReceiveMessagesThread.setActivity(this);
    ReceiveMessagesThread.setListener(MainActivity.this);

    new Thread(new ReceiveMessagesThread()).start();

    if (getIntent().hasExtra("albums")) {
      generateAlbums(getIntent().getExtras().getString("albums"));
    } else if (!SessionDataCache.getAlbumNames().isEmpty()) {
      generateAlbums(joinLines(SessionDataCache.getAlbumNames()));
    } else {
      generateAlbums("");
    }
    updateAlbumCache();

    selectionMode = false;
    awaitingAlbumEmptyCheck = false;
    albumToCheckForEmpty = "";

    albumMainRecyclerView = findViewById(R.id.albumMainRecyclerView);

    albumMainRecyclerView.setHasFixedSize(true);
    layoutManager = new LinearLayoutManager(this);
    adapter = new AlbumAdapter(getApplicationContext(), albums, this);

    albumMainRecyclerView.setLayoutManager(layoutManager);
    albumMainRecyclerView.setAdapter(adapter);
  }

  @Override
  protected void onResume() {
    super.onResume();
    ReceiveMessagesThread.setActivity(this);
    ReceiveMessagesThread.setListener(MainActivity.this);
    new Thread(new ReceiveMessagesThread()).start();
    new Thread(
            new SendMessagesThread(
                "ALBUMS", MessageCodes.getRequest(), buildAlbumsDeltaRequestPayload()))
        .start();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.albums_menu, menu);

    MenuItem searchItem = menu.findItem(R.id.searchAlbumsMenuItem);
    SearchView searchView = (SearchView) searchItem.getActionView();
    searchView.setQueryHint(getString(R.string.search_albums));
    ImageView searchIcon = searchView.findViewById(androidx.appcompat.R.id.search_mag_icon);
    if (searchIcon != null) {
      searchIcon.setColorFilter(ContextCompat.getColor(this, R.color.purple_500));
    }
    searchView.setOnQueryTextListener(
        new SearchView.OnQueryTextListener() {
          @Override
          public boolean onQueryTextSubmit(String query) {
            activeQuery = query == null ? "" : query;
            filterAlbums(query);
            return true;
          }

          @Override
          public boolean onQueryTextChange(String newText) {
            activeQuery = newText == null ? "" : newText;
            filterAlbums(newText);
            return true;
          }
        });

    optionsMenu = menu;
    refreshSelectionUi();

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
        startActivity(goCreateAlbum);
        return true;
      case R.id.settingsAlbumsMenuItem:
        if (selectionMode) {
          return true;
        }
        Intent goSettings = new Intent(getApplicationContext(), SettingsActivity.class);
        if (getIntent().hasExtra("albums")) {
          goSettings.putExtra("albums", getIntent().getExtras().getString("albums"));
        }
        startActivity(goSettings);
        return true;
      case R.id.aboutAlbumsMenuItem:
        if (selectionMode) {
          return true;
        }
        Intent goAbout = new Intent(getApplicationContext(), AboutActivity.class);
        if (getIntent().hasExtra("albums")) {
          goAbout.putExtra("albums", getIntent().getExtras().getString("albums"));
        }
        startActivity(goAbout);
        return true;
      case R.id.deleteAlbumsMenuItem:
        requestDeleteSelectedAlbums();
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

  private void mergeAlbums(String message) {
    if (message == null || message.trim().isEmpty()) {
      return;
    }
    String[] albumsMessage = message.split("\\n");
    for (String rawName : albumsMessage) {
      String albumName = rawName == null ? "" : rawName.trim();
      if (albumName.isEmpty()) {
        continue;
      }
      boolean exists = false;
      for (AlbumItem item : allAlbums) {
        if (item.getAlbumName().equals(albumName)) {
          exists = true;
          break;
        }
      }
      if (!exists) {
        allAlbums.add(new AlbumItem(albumName));
      }
    }
  }

  private String buildAlbumsDeltaRequestPayload() {
    if (allAlbums == null || allAlbums.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder("DELTA");
    for (AlbumItem item : allAlbums) {
      sb.append("\n").append(item.getAlbumName());
    }
    return sb.toString();
  }

  private String joinLines(List<String> values) {
    if (values == null || values.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        sb.append("\n");
      }
      sb.append(values.get(i));
    }
    return sb.toString();
  }

  private void updateAlbumCache() {
    ArrayList<String> names = new ArrayList<>();
    for (AlbumItem item : allAlbums) {
      names.add(item.getAlbumName());
    }
    SessionDataCache.setAlbumNames(names);
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

  private String buildAlbumsPayloadFromAll() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < allAlbums.size(); i++) {
      if (i > 0) {
        sb.append("\n");
      }
      sb.append(allAlbums.get(i).getAlbumName());
    }
    return sb.toString();
  }

  private void refreshSelectionUi() {
    if (optionsMenu == null) {
      return;
    }
    MenuItem addItem = optionsMenu.findItem(R.id.addAlbumsMenuItem);
    MenuItem settingsItem = optionsMenu.findItem(R.id.settingsAlbumsMenuItem);
    MenuItem aboutItem = optionsMenu.findItem(R.id.aboutAlbumsMenuItem);
    MenuItem searchItem = optionsMenu.findItem(R.id.searchAlbumsMenuItem);
    MenuItem deleteItem = optionsMenu.findItem(R.id.deleteAlbumsMenuItem);

    boolean hasSelection = !selectedAlbumNames.isEmpty();
    if (addItem != null) {
      addItem.setVisible(!selectionMode);
    }
    if (settingsItem != null) {
      settingsItem.setVisible(!selectionMode);
    }
    if (aboutItem != null) {
      aboutItem.setVisible(!selectionMode);
    }
    if (searchItem != null) {
      searchItem.setVisible(!selectionMode);
    }
    if (deleteItem != null) {
      deleteItem.setVisible(selectionMode);
      deleteItem.setEnabled(hasSelection);
    }
  }

  private void clearSelectionMode() {
    selectionMode = false;
    selectedAlbumNames.clear();
    refreshSelectionUi();
    adapter.notifyDataSetChanged();
  }

  private void requestDeleteSelectedAlbums() {
    if (selectedAlbumNames.isEmpty()) {
      return;
    }

    if (selectedAlbumNames.size() == 1) {
      albumToCheckForEmpty = selectedAlbumNames.iterator().next();
      awaitingAlbumEmptyCheck = true;
      new Thread(new SendMessagesThread("PHOTOS", MessageCodes.getRequest(), albumToCheckForEmpty))
          .start();
      return;
    }

    showDeleteConfirmDialog(false);
  }

  private void showDeleteConfirmDialog(boolean singleAlbum) {
    String title =
        singleAlbum
            ? getString(R.string.delete_album_confirm_title)
            : getString(R.string.delete_albums_confirm_title);
    String message =
        singleAlbum
            ? getString(R.string.delete_album_confirm_message)
            : getString(R.string.delete_albums_confirm_message);

    new AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(
            getString(R.string.confirm),
            (DialogInterface dialog, int which) -> scheduleAlbumDelete(false))
        .setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.dismiss())
        .show();
  }

  private void scheduleAlbumDelete(boolean useRedoLabel) {
    if (pendingDeleteRunnable != null) {
      deleteHandler.removeCallbacks(pendingDeleteRunnable);
      pendingDeleteRunnable = null;
    }

    pendingDeletedAlbums.clear();
    pendingDeleteAlbumNames.clear();

    ArrayList<String> toDelete = new ArrayList<>(selectedAlbumNames);
    for (String albumName : toDelete) {
      for (AlbumItem item : allAlbums) {
        if (item.getAlbumName().equals(albumName)) {
          pendingDeletedAlbums.add(item);
          pendingDeleteAlbumNames.add(albumName);
          break;
        }
      }
    }

    removeAlbumsByName(toDelete);
    clearSelectionMode();

    pendingDeleteRunnable = this::sendPendingAlbumDeleteToServer;

    deleteHandler.postDelayed(pendingDeleteRunnable, 5000);

    Snackbar.make(albumMainRecyclerView, getString(R.string.albums_deleted), Snackbar.LENGTH_LONG)
        .setDuration(5000)
        .setAction(
            useRedoLabel ? getString(R.string.redo) : getString(R.string.undo),
            v -> {
              if (pendingDeleteRunnable != null) {
                deleteHandler.removeCallbacks(pendingDeleteRunnable);
                pendingDeleteRunnable = null;
              }
              restorePendingDeletedAlbums();
            })
        .show();
  }

  private void sendPendingAlbumDeleteToServer() {
    if (pendingDeleteRunnable != null) {
      deleteHandler.removeCallbacks(pendingDeleteRunnable);
      pendingDeleteRunnable = null;
    }
    if (pendingDeleteAlbumNames.isEmpty()) {
      return;
    }
    StringBuilder payload = new StringBuilder();
    for (int i = 0; i < pendingDeleteAlbumNames.size(); i++) {
      if (i > 0) {
        payload.append("\n");
      }
      payload.append(pendingDeleteAlbumNames.get(i));
    }
    new Thread(new SendMessagesThread("DEL_ALBUMS", MessageCodes.getRequest(), payload.toString()))
        .start();
  }

  private void removeAlbumsByName(ArrayList<String> names) {
    ArrayList<AlbumItem> updatedAll = new ArrayList<>();
    for (AlbumItem item : allAlbums) {
      if (!names.contains(item.getAlbumName())) {
        updatedAll.add(item);
      }
    }
    allAlbums.clear();
    allAlbums.addAll(updatedAll);
    filterAlbums(activeQuery);
    updateAlbumCache();
  }

  private void restorePendingDeletedAlbums() {
    if (pendingDeletedAlbums.isEmpty()) {
      return;
    }
    allAlbums.addAll(pendingDeletedAlbums);
    pendingDeletedAlbums.clear();
    pendingDeleteAlbumNames.clear();
    filterAlbums(activeQuery);
    updateAlbumCache();
  }

  @Override
  public void onBackPressed() {
    if (selectionMode) {
      clearSelectionMode();
      return;
    }
    super.onBackPressed();
  }

  @Override
  protected void onStop() {
    super.onStop();
    if (pendingDeleteRunnable != null && !pendingDeleteAlbumNames.isEmpty()) {
      deleteHandler.removeCallbacks(pendingDeleteRunnable);
      sendPendingAlbumDeleteToServer();
      pendingDeleteRunnable = null;
    }
  }

  @Override
  public void onOpenAlbum(String albumName) {
    Intent goSecond = new Intent(getApplicationContext(), SecondActivity.class);
    goSecond.putExtra("album_name", albumName);
    goSecond.putExtra("albums", buildAlbumsPayloadFromAll());
    startActivity(goSecond);
  }

  @Override
  public void onToggleAlbumSelection(String albumName) {
    if (!selectionMode) {
      selectionMode = true;
    }
    if (selectedAlbumNames.contains(albumName)) {
      selectedAlbumNames.remove(albumName);
    } else {
      selectedAlbumNames.add(albumName);
    }
    if (selectedAlbumNames.isEmpty()) {
      selectionMode = false;
    }
    refreshSelectionUi();
    adapter.notifyDataSetChanged();
  }

  @Override
  public boolean isSelectionModeEnabled() {
    return selectionMode;
  }

  @Override
  public boolean isAlbumSelected(String albumName) {
    return selectedAlbumNames.contains(albumName);
  }

  @Override
  public void messageReceived(String mes, Activity activity) {
    HandelMessage message = new HandelMessage(mes);
    if (message.getName().equals("ALBUMS")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        mergeAlbums(message.getData());
        filterAlbums(activeQuery);
        updateAlbumCache();
      } else if (message.getType().equals(MessageCodes.getAlbumsError())) {
        Toast.makeText(
                getApplicationContext(),
                getResources().getString(R.string.albums_error),
                Toast.LENGTH_SHORT)
            .show();
      }
      return;
    }

    if ((message.getName().equals("PHOTOS") || message.getName().equals("PHOTOS_COUNT"))
        && awaitingAlbumEmptyCheck) {
      awaitingAlbumEmptyCheck = false;
      boolean isEmpty = false;
      if (message.getName().equals("PHOTOS_COUNT")
          && message.getType().equals(MessageCodes.getConfirm())) {
        String[] lines = message.getData() == null ? new String[0] : message.getData().split("\n");
        if (lines.length >= 2) {
          try {
            isEmpty = Integer.parseInt(lines[1]) == 0;
          } catch (NumberFormatException ignored) {
            isEmpty = false;
          }
        }
      } else if (message.getType().equals(MessageCodes.getConfirm())) {
        String data = message.getData();
        String[] lines = data == null ? new String[0] : data.split("\n");
        isEmpty = lines.length <= 1;
      }

      if (isEmpty) {
        scheduleAlbumDelete(true);
      } else {
        showDeleteConfirmDialog(true);
      }
      albumToCheckForEmpty = "";
      return;
    }

    if (message.getName().equals("DEL_ALBUMS")) {
      if (message.getType().equals(MessageCodes.getConfirm())) {
        pendingDeleteAlbumNames.clear();
        pendingDeletedAlbums.clear();
      } else if (message.getType().equals(MessageCodes.getDelAlbumError())) {
        restorePendingDeletedAlbums();
        Toast.makeText(
                getApplicationContext(),
                getResources().getString(R.string.delete_album_error),
                Toast.LENGTH_SHORT)
            .show();
      }
    }
  }
}
