package com.example.pcloud;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import java.util.Objects;

public class SettingsActivity extends AppCompatActivity implements ReceiveMessagesListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        ReceiveMessagesThread.setActivity(this);
        ReceiveMessagesThread.setListener(SettingsActivity.this);

        new Thread(new ReceiveMessagesThread()).start();

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
        if (getIntent().hasExtra("albums")){
            goMain.putExtra("albums", getIntent().getExtras().getString("albums"));
        }
        MySocket.setClosed(true);
        startActivity(goMain);
    }

    @Override
    public void messageReceived(String mes, Activity activity) {

    }
}