package com.example.pcloud;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ClientLogger {
  private static final String TAG = "PCloudClient";
  private static final String LOG_FILE_NAME = "client-debug.log";
  private static volatile File logFile;

  private ClientLogger() {}

  public static synchronized void init(Context context) {
    if (logFile == null) {
      logFile = new File(context.getFilesDir(), LOG_FILE_NAME);
    }
  }

  public static void installCrashHandler(Context context) {
    init(context);
    Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(
        (thread, throwable) -> {
          logError("CRASH", "Unhandled exception in thread: " + thread.getName(), throwable);
          if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable);
          }
        });
  }

  public static synchronized void log(String source, String message) {
    String line = formatLine("INFO", source, message, null);
    Log.i(TAG, source + " :: " + message);
    append(line);
  }

  public static synchronized void logError(String source, String message, Throwable throwable) {
    String line = formatLine("ERROR", source, message, throwable);
    Log.e(TAG, source + " :: " + message, throwable);
    append(line);
  }

  public static synchronized String getLogPath(Context context) {
    init(context);
    return logFile.getAbsolutePath();
  }

  private static String formatLine(
      String level, String source, String message, Throwable throwable) {
    String timestamp =
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    StringBuilder builder = new StringBuilder();
    builder
        .append(timestamp)
        .append(" [")
        .append(level)
        .append("] ")
        .append(source)
        .append(" - ")
        .append(message);
    if (throwable != null) {
      builder.append(" | ").append(throwable.toString());
    }
    return builder.append("\n").toString();
  }

  private static void append(String line) {
    if (logFile == null) {
      return;
    }
    FileWriter writer = null;
    try {
      writer = new FileWriter(logFile, true);
      writer.write(line);
    } catch (IOException ignored) {
    } finally {
      if (writer != null) {
        try {
          writer.close();
        } catch (IOException ignored) {
        }
      }
    }
  }
}
