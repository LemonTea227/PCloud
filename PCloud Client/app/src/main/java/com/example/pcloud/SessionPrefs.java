package com.example.pcloud;

import android.content.Context;
import android.content.SharedPreferences;

public final class SessionPrefs {
  private static final String PREF_NAME = "pcloud_prefs";
  private static final String KEY_KEEP_LOGGED_IN = "keep_logged_in";
  private static final String KEY_USERNAME = "saved_username";
  private static final String KEY_PASSWORD = "saved_password";

  private SessionPrefs() {}

  private static SharedPreferences prefs(Context context) {
    return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
  }

  public static boolean shouldKeepLoggedIn(Context context) {
    return prefs(context).getBoolean(KEY_KEEP_LOGGED_IN, false);
  }

  public static void setKeepLoggedIn(Context context, boolean keepLoggedIn) {
    prefs(context).edit().putBoolean(KEY_KEEP_LOGGED_IN, keepLoggedIn).apply();
  }

  public static void saveCredentials(Context context, String username, String password) {
    prefs(context)
        .edit()
        .putString(KEY_USERNAME, username)
        .putString(KEY_PASSWORD, password)
        .apply();
  }

  public static String getSavedUsername(Context context) {
    return prefs(context).getString(KEY_USERNAME, "");
  }

  public static String getSavedPassword(Context context) {
    return prefs(context).getString(KEY_PASSWORD, "");
  }

  public static void clearCredentials(Context context) {
    prefs(context).edit().remove(KEY_USERNAME).remove(KEY_PASSWORD).apply();
  }
}
