package com.example.pcloud;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
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
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.espresso.contrib.RecyclerViewActions;
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
public class ClientE2ETest {
  private MockPCloudServer server;

  @Before
  public void setUp() throws IOException {
    server = new MockPCloudServer();
    server.seedUser("e2elogin", "Passw0rd!");
    server.start();

    Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    SessionPrefs.setKeepLoggedIn(appContext, false);
    SessionPrefs.clearCredentials(appContext);
    MySocket.setIP("127.0.0.1");
    MySocket.setAESkey(new byte[0]);
    MySocket.setExtraMessage("");
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

    if (server != null) {
      server.stop();
    }
  }

  @Test
  public void e2e_register_createAlbum_uploadPhoto_and_openPhotoViewer() {
    launchSplashAndWaitForLogin();

    onView(withId(R.id.registerButtonLogin)).perform(click());

    String username = "e2euser";
    String password = "Passw0rd!";
    onView(withId(R.id.usernameRegister)).perform(replaceText(username), closeSoftKeyboard());
    onView(withId(R.id.passwordRegister)).perform(replaceText(password), closeSoftKeyboard());
    onView(withId(R.id.confirmPasswordRegister))
        .perform(replaceText(password), closeSoftKeyboard());
    onView(withId(R.id.firstNameRegister)).perform(replaceText("Eee"), closeSoftKeyboard());
    onView(withId(R.id.lastNameRegister)).perform(replaceText("User"), closeSoftKeyboard());
    onView(withId(R.id.birthDateRegister)).perform(replaceText("1/1/2000"), closeSoftKeyboard());
    onView(withId(R.id.registerButtonRegister)).perform(click());

    waitForView(withId(R.id.albumMainRecyclerView), 12000);

    String albumName = "e2e_album";
    onView(withId(R.id.addAlbumsMenuItem)).perform(click());
    waitForView(withId(R.id.albumNameCreateAlbum), 5000);
    onView(withId(R.id.albumNameCreateAlbum)).perform(replaceText(albumName), closeSoftKeyboard());
    onView(withId(R.id.createAlbumButton)).perform(click());

    waitForView(withText(albumName), 10000);
    assertTrue(server.hasAlbum(albumName));

    onView(withId(R.id.albumMainRecyclerView))
      .perform(RecyclerViewActions.actionOnItemAtPosition(0, click()));
    waitForView(withId(R.id.photosSecondRecyclerView), 8000);

    triggerPhotoPickResultOnSecondActivity();

    waitUntil(() -> server.hasUploadedPhoto(albumName, "e2e_image.png"), 8000);

    onView(withId(R.id.firstPhotoImageButton)).perform(click());
    waitForView(withId(R.id.photoViewerPhotoView), 8000);
  }

  @Test
  public void e2e_login_and_settings_toggle_keepLoggedIn() {
    launchSplashAndWaitForLogin();

    onView(withId(R.id.usernameLogin)).perform(replaceText("e2elogin"), closeSoftKeyboard());
    onView(withId(R.id.passwordLogin)).perform(replaceText("Passw0rd!"), closeSoftKeyboard());
    onView(withId(R.id.rememberMeLoginCheckBox)).perform(click());
    onView(withId(R.id.loginButtonLogin)).perform(click());

    waitForView(withId(R.id.albumMainRecyclerView), 10000);

    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    openActionBarOverflowOrOptionsMenu(context);
    onView(withText(context.getString(R.string.about))).perform(click());
    waitForView(withId(R.id.appNameAboutTextView), 5000);
    pressBack();
    waitForView(withId(R.id.albumMainRecyclerView), 8000);

    boolean opened = false;
    try {
      onView(withId(R.id.settingsAlbumsMenuItem)).perform(click());
      opened = true;
    } catch (Exception ignored) {
    }
    if (!opened) {
      openActionBarOverflowOrOptionsMenu(context);
      onView(withText("Settings")).perform(click());
    }

    waitForView(withId(R.id.keepLoggedInSettingsSwitch), 5000);
    onView(withId(R.id.keepLoggedInSettingsSwitch)).check(matches(isChecked()));

    onView(withId(R.id.keepLoggedInSettingsSwitch)).perform(click());
    onView(withId(R.id.keepLoggedInSettingsSwitch)).check(matches(not(isChecked())));
  }

  private void launchSplashAndWaitForLogin() {
    ActivityScenario.launch(SplashActivity.class);
    waitForView(withId(R.id.usernameLogin), 12000);
  }

  private void triggerPhotoPickResultOnSecondActivity() {
    Activity activity = getCurrentActivity();
    assertTrue(activity instanceof SecondActivity);

    Intent resultIntent = new Intent();
    resultIntent.setData(createTempImageUri());

    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () ->
                ((SecondActivity) activity)
                    .onActivityResult(1, Activity.RESULT_OK, resultIntent));
  }

  private Uri createTempImageUri() {
    try {
      Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
      File imageFile = new File(context.getCacheDir(), "e2e_image.png");
      Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
      bitmap.eraseColor(0xFFFF8800);
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
                  ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED);
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
      SystemClock.sleep(150);
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
