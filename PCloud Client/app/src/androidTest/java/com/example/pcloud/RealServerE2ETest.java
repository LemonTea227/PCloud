package com.example.pcloud;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RealServerE2ETest {

  @Before
  public void setUp() {
    Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    SessionPrefs.setKeepLoggedIn(appContext, false);
    SessionPrefs.clearCredentials(appContext);

    MySocket.setIP("10.0.2.2");
    MySocket.setAESkey(new byte[0]);
    MySocket.setExtraMessage("");
    MySocket.setSocket(null);
    MySocket.setInput(null);
    MySocket.setOutput(null);
    MySocket.setClosed(false);
  }

  @After
  public void tearDown() {
    try {
      if (MySocket.getSocket() != null) {
        MySocket.getSocket().close();
      }
    } catch (IOException ignored) {
    }
    MySocket.setSocket(null);
    MySocket.setInput(null);
    MySocket.setOutput(null);
    MySocket.setAESkey(new byte[0]);
    MySocket.setExtraMessage("");
    MySocket.setClosed(true);

    Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    SessionPrefs.setKeepLoggedIn(appContext, false);
    SessionPrefs.clearCredentials(appContext);
  }

  @Test
  public void realServer_create_album_upload_photo_delete_photo() {
    launchSplashAndWaitForEntry();
    registerAndLoginRealUser();

    waitForView(withId(R.id.albumMainRecyclerView), 20000);

    String albumName = "real_e2e_" + System.currentTimeMillis();
    clickMenuItem(R.id.addAlbumsMenuItem, R.string.create_album_text);
    waitForView(withId(R.id.albumNameCreateAlbum), 8000);
    onView(withId(R.id.albumNameCreateAlbum)).perform(replaceText(albumName), closeSoftKeyboard());
    onView(withId(R.id.createAlbumButton)).perform(click());

    waitUntil(
        () -> {
          try {
            onView(withId(R.id.albumMainRecyclerView))
                .perform(RecyclerViewActions.scrollTo(hasDescendant(withText(albumName))));
            return true;
          } catch (Throwable ignored) {
            return false;
          }
        },
        20000);
    onView(withId(R.id.albumMainRecyclerView))
        .perform(RecyclerViewActions.actionOnItem(hasDescendant(withText(albumName)), click()));

    waitForView(withId(R.id.photosSecondRecyclerView), 12000);
    triggerPhotoPickResultOnSecondActivity();
    waitForView(withId(R.id.firstPhotoImageButton), 35000);

    onView(withId(R.id.firstPhotoImageButton))
        .perform(androidx.test.espresso.action.ViewActions.longClick());
    clickMenuItem(R.id.deletePhotosMenuItem, R.string.delete);

    waitForView(withId(R.id.photosSecondRecyclerView), 15000);
    pressBack();
    waitForView(withId(R.id.albumMainRecyclerView), 12000);
    onView(withId(R.id.albumMainRecyclerView))
        .perform(RecyclerViewActions.scrollTo(hasDescendant(withText(albumName))));
    onView(withId(R.id.albumMainRecyclerView))
        .perform(RecyclerViewActions.actionOnItem(hasDescendant(withText(albumName)), click()));
    waitForView(withId(R.id.photosSecondRecyclerView), 12000);

    waitUntil(
        () -> {
          try {
            onView(withId(R.id.firstPhotoImageButton)).check(matches(not(isDisplayed())));
            return true;
          } catch (NoMatchingViewException ignored) {
            return true;
          } catch (Throwable ignored) {
            return false;
          }
        },
        15000);
  }

  private void launchSplashAndWaitForEntry() {
    ActivityScenario.launch(SplashActivity.class);
    waitUntil(
        () ->
            isViewDisplayed(withId(R.id.usernameLogin))
                || isViewDisplayed(withId(R.id.albumMainRecyclerView)),
        60000);
  }

  private void registerAndLoginRealUser() {
    if (isViewDisplayed(withId(R.id.albumMainRecyclerView))) {
      return;
    }
    waitForView(withId(R.id.usernameLogin), 40000);

    String username = "realsrv" + System.currentTimeMillis();
    String password = "Passw0rd!";

    onView(withId(R.id.registerButtonLogin)).perform(click());
    waitForView(withId(R.id.usernameRegister), 12000);

    onView(withId(R.id.usernameRegister)).perform(replaceText(username), closeSoftKeyboard());
    onView(withId(R.id.passwordRegister)).perform(replaceText(password), closeSoftKeyboard());
    onView(withId(R.id.confirmPasswordRegister))
        .perform(replaceText(password), closeSoftKeyboard());
    onView(withId(R.id.firstNameRegister)).perform(replaceText("Real"), closeSoftKeyboard());
    onView(withId(R.id.lastNameRegister)).perform(replaceText("Server"), closeSoftKeyboard());
    onView(withId(R.id.birthDateRegister)).perform(replaceText("1/1/2000"), closeSoftKeyboard());
    onView(withId(R.id.registerButtonRegister)).perform(click());

    waitForView(withId(R.id.albumMainRecyclerView), 60000);
  }

  private void clickMenuItem(int itemId, int textResId) {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    try {
      onView(withId(itemId)).perform(click());
      return;
    } catch (Throwable ignored) {
    }

    try {
      onView(withText(context.getString(textResId))).perform(click());
      return;
    } catch (Throwable ignored) {
    }

    openActionBarOverflowOrOptionsMenu(context);
    onView(withText(context.getString(textResId))).perform(click());
  }

  private boolean isViewDisplayed(org.hamcrest.Matcher<android.view.View> matcher) {
    try {
      onView(matcher).check(matches(isDisplayed()));
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  private void triggerPhotoPickResultOnSecondActivity() {
    Activity activity = getCurrentActivity();
    assertTrue(activity instanceof SecondActivity);

    Intent resultIntent = new Intent();
    resultIntent.setData(createTempImageUri());

    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () ->
                ((SecondActivity) activity).onActivityResult(1, Activity.RESULT_OK, resultIntent));
  }

  private Uri createTempImageUri() {
    try {
      Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
      File imageFile = new File(context.getCacheDir(), "real_e2e_image.png");
      int width = 400;
      int height = 400;
      Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      int[] pixels = new int[width * height];
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          int i = y * width + x;
          int r = (x * 31 + y * 17) & 0xFF;
          int g = (x * 13 + y * 29) & 0xFF;
          int b = (x * 7 + y * 43) & 0xFF;
          pixels[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
      }
      bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
      FileOutputStream outputStream = new FileOutputStream(imageFile);
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
      outputStream.flush();
      outputStream.close();
      return Uri.fromFile(imageFile);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Activity getCurrentActivity() {
    final Activity[] current = new Activity[1];
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              Collection<Activity> resumed =
                  ActivityLifecycleMonitorRegistry.getInstance()
                      .getActivitiesInStage(Stage.RESUMED);
              if (!resumed.isEmpty()) {
                current[0] = resumed.iterator().next();
              }
            });
    return current[0];
  }

  private interface Condition {
    boolean done();
  }

  private void waitUntil(Condition condition, long timeoutMs) {
    long endTime = SystemClock.uptimeMillis() + timeoutMs;
    while (SystemClock.uptimeMillis() < endTime) {
      if (condition.done()) {
        return;
      }
      SystemClock.sleep(200);
    }
    throw new AssertionError(String.format(Locale.US, "Condition not met within %dms", timeoutMs));
  }

  private void waitForView(org.hamcrest.Matcher<android.view.View> matcher, long timeoutMs) {
    waitUntil(
        () -> {
          try {
            onView(matcher).check(matches(isDisplayed()));
            return true;
          } catch (Throwable ignored) {
            return false;
          }
        },
        timeoutMs);
  }
}
