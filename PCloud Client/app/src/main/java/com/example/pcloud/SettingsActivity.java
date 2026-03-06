package com.example.pcloud;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.util.Objects;

public class SettingsActivity extends AppCompatActivity implements ReceiveMessagesListener {
  private SwitchMaterial keepLoggedInSwitch;
  private TextView logPathTextView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_settings);

    ReceiveMessagesThread.setActivity(this);
    ReceiveMessagesThread.setListener(SettingsActivity.this);

    new Thread(new ReceiveMessagesThread()).start();

    keepLoggedInSwitch = findViewById(R.id.keepLoggedInSettingsSwitch);
    logPathTextView = findViewById(R.id.logPathSettingsTextView);

    keepLoggedInSwitch.setChecked(SessionPrefs.shouldKeepLoggedIn(this));
    keepLoggedInSwitch.setOnCheckedChangeListener(
        (buttonView, isChecked) -> {
          SessionPrefs.setKeepLoggedIn(this, isChecked);
          if (!isChecked) {
            SessionPrefs.clearCredentials(this);
          }
          ClientLogger.log("SettingsActivity", "keepLoggedIn updated to=" + isChecked);
        });

    logPathTextView.setText(
        getString(
            R.string.client_log_path_value, ClientLogger.getLogPath(getApplicationContext())));

    Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setDisplayShowHomeEnabled(true);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      onBackPressed();
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onBackPressed() {
    Intent goMain = new Intent(getApplicationContext(), MainActivity.class);
    if (getIntent().hasExtra("albums")) {
      goMain.putExtra("albums", getIntent().getExtras().getString("albums"));
    }
    startActivity(goMain);
  }

  @Override
  public void messageReceived(String mes, Activity activity) {}
}
