package com.example.pcloud;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Objects;

public class AddPhotoActivity extends AppCompatActivity implements ReceiveMessagesListener {
    private static final int RESULT_LOAD_IMAGE = 1;
    Boolean imageSelected = false;
    String mimeType = "";
    String nameOfFile = "";

    ImageButton choosePhotoAddPhotoImageButton;
    Button addPhotoButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_photo);
        java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(getApplicationContext());

        ReceiveMessagesThread.setActivity(this);
        ReceiveMessagesThread.setListener(AddPhotoActivity.this);

        new Thread(new ReceiveMessagesThread()).start();


        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);



        if (getIntent().hasExtra("photos")) {
            setTitle(getIntent().getExtras().getString("photos").split("\n")[0]);
        }

        choosePhotoAddPhotoImageButton = findViewById(R.id.choosePhotoAddPhotoImageButton);
        addPhotoButton = findViewById(R.id.addPhotoButton);

        addPhotoButton.setOnClickListener(v -> {
            try {
                InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);

            } catch (NullPointerException ignored) {
            }

            if (imageSelected) {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                Bitmap imageToUpload = ((BitmapDrawable) choosePhotoAddPhotoImageButton.getDrawable()).getBitmap();
                if (mimeType.equals("jpeg") || mimeType.equals("jpg")) {
                    imageToUpload.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
                } else if (mimeType.equals("png")) {
                    imageToUpload.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
                }
                String encodedImage = Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.NO_WRAP);
                if (getIntent().hasExtra("photos")) {
//                    Toast.makeText(getApplicationContext(), mimeType, Toast.LENGTH_SHORT).show();
                    String albumName = getIntent().getExtras().getString("photos").split("\n")[0];
                    new Thread(new SendMessagesThread("UPLOAD_PHOTO", MessageCodes.getRequest(), albumName + "\n" + nameOfFile + "\n" + encodedImage)).start();
                } else {
                    new Thread(new SendMessagesThread("UPLOAD_PHOTO", MessageCodes.getRequest(), nameOfFile + "\n" + encodedImage)).start();
                }

            }
            else {
                Intent goGallery = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                MySocket.setClosed(true);
                startActivityForResult(goGallery, RESULT_LOAD_IMAGE);
            }

        });

        choosePhotoAddPhotoImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent goGallery = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                MySocket.setClosed(true);
                startActivityForResult(goGallery, RESULT_LOAD_IMAGE);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ReceiveMessagesThread.setActivity(this);
        ReceiveMessagesThread.setListener(AddPhotoActivity.this);
        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && data != null) {
            android.net.Uri selectedImage = data.getData();
            nameOfFile = getFileName(selectedImage);
            String[] mimeTypeSplit = getContentResolver().getType(selectedImage).split("/");
            mimeType = mimeTypeSplit[mimeTypeSplit.length - 1];
            choosePhotoAddPhotoImageButton.setImageURI(selectedImage);
            imageSelected = true;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Intent goSecond = new Intent(getApplicationContext(), SecondActivity.class);
            if (getIntent().hasExtra("albums")) {
                goSecond.putExtra("albums", getIntent().getExtras().getString("albums"));
            }

            if (getIntent().hasExtra("photos")) {
                goSecond.putExtra("photos", getIntent().getExtras().getString("photos"));
            }

            MySocket.setClosed(true);
            startActivity(goSecond);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (imageSelected) {
            choosePhotoAddPhotoImageButton.setImageResource(R.drawable.photo_icon);
            imageSelected = false;
        } else {
            Intent goSecond = new Intent(getApplicationContext(), SecondActivity.class);
            if (getIntent().hasExtra("albums")) {
                goSecond.putExtra("albums", getIntent().getExtras().getString("albums"));
            }

            if (getIntent().hasExtra("photos")) {
                goSecond.putExtra("photos", getIntent().getExtras().getString("photos"));
            }

            MySocket.setClosed(true);
            startActivity(goSecond);
        }
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

    public String getFileName(android.net.Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
}