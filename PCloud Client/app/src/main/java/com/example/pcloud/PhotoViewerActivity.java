package com.example.pcloud;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.Toast;

import com.github.chrisbanes.photoview.PhotoView;

import java.util.Base64;
import java.util.Objects;

public class PhotoViewerActivity extends AppCompatActivity implements ReceiveMessagesListener {
    PhotoView photoViewerPhotoView;
//    ImageView photoViewerImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_viewer);
        java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(getApplicationContext());

        ReceiveMessagesThread.setActivity(this);
        ReceiveMessagesThread.setListener(PhotoViewerActivity.this);

        new Thread(new ReceiveMessagesThread()).start();

        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        photoViewerPhotoView = findViewById(R.id.photoViewerPhotoView);
//        photoViewerImageView = findViewById(R.id.photoViewerImageView);

        if (getIntent().hasExtra("album_name")) {
            setTitle(getIntent().getExtras().getString("album_name"));
        }

        if (getIntent().hasExtra("album_name") && getIntent().hasExtra("photo_name")) {
            new Thread(new SendMessagesThread("PHOTO", MessageCodes.getRequest(), getIntent().getExtras().getString("album_name") + "\n" + getIntent().getExtras().getString("photo_name"))).start();
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        Intent goSecond = new Intent(getApplicationContext(), SecondActivity.class);
        if (getIntent().hasExtra("albums")) {
            goSecond.putExtra("albums", getIntent().getExtras().getString("albums"));
        }

        if (getIntent().hasExtra("album_name")) {
            goSecond.putExtra("album_name", getIntent().getExtras().getString("album_name"));
        }

        MySocket.setClosed(true);
        goSecond.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(goSecond);
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void messageReceived(String mes, Activity activity) {
        HandelMessage message = new HandelMessage(mes);
        if (message.getName().equals("PHOTO")) {
            if (message.getType().equals(MessageCodes.getConfirm())) {
                byte[] decodedString = Base64.getDecoder().decode(message.getData());
                Bitmap decoded = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                photoViewerPhotoView.setImageBitmap(decoded);
            } else if (message.getType().equals(MessageCodes.getPhotoError())) {
                Toast.makeText(getApplicationContext(), getResources().getString(R.string.photos_error), Toast.LENGTH_SHORT).show();
            }
        }
    }
}