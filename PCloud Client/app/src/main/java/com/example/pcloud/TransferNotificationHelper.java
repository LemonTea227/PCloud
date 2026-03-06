package com.example.pcloud;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public final class TransferNotificationHelper {
  private static final String CHANNEL_ID = "pcloud_transfers";
  private static final int UPLOAD_NOTIFICATION_ID = 41001;
  private static final int DOWNLOAD_NOTIFICATION_ID = 41002;
  private static boolean channelCreated = false;

  private TransferNotificationHelper() {}

  private static void ensureChannel(Context context) {
    if (channelCreated) {
      return;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel =
          new NotificationChannel(
              CHANNEL_ID, "PCloud Transfers", NotificationManager.IMPORTANCE_LOW);
      channel.setDescription("Upload and download progress");
      NotificationManager manager =
          (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
      if (manager != null) {
        manager.createNotificationChannel(channel);
      }
    }
    channelCreated = true;
  }

  private static boolean canPostNotifications(Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      return true;
    }
    return ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS)
        == PackageManager.PERMISSION_GRANTED;
  }

  private static void safeNotify(Context context, int id, NotificationCompat.Builder builder) {
    if (!canPostNotifications(context)) {
      return;
    }
    try {
      NotificationManagerCompat.from(context).notify(id, builder.build());
    } catch (SecurityException ignored) {
    }
  }

  private static void safeCancel(Context context, int id) {
    try {
      NotificationManagerCompat.from(context).cancel(id);
    } catch (SecurityException ignored) {
    }
  }

  public static void showUploadProgress(Context context, int done, int total) {
    ensureChannel(context);
    NotificationCompat.Builder builder =
        new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Uploading photo")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW);

    if (total > 0) {
      int safeDone = Math.max(0, Math.min(done, total));
      builder.setContentText(safeDone + "/" + total + " parts").setProgress(total, safeDone, false);
    } else {
      builder.setContentText("Preparing upload").setProgress(0, 0, true);
    }

    safeNotify(context, UPLOAD_NOTIFICATION_ID, builder);
  }

  public static void completeUpload(Context context) {
    safeCancel(context, UPLOAD_NOTIFICATION_ID);
  }

  public static void failUpload(Context context) {
    safeCancel(context, UPLOAD_NOTIFICATION_ID);
  }

  public static void showDownloadProgress(Context context, int done, int total) {
    ensureChannel(context);
    NotificationCompat.Builder builder =
        new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading photo")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW);

    if (total > 0) {
      int safeDone = Math.max(0, Math.min(done, total));
      builder.setContentText(safeDone + "/" + total + " parts").setProgress(total, safeDone, false);
    } else {
      builder.setContentText("Preparing download").setProgress(0, 0, true);
    }

    safeNotify(context, DOWNLOAD_NOTIFICATION_ID, builder);
  }

  public static void completeDownload(Context context) {
    safeCancel(context, DOWNLOAD_NOTIFICATION_ID);
  }

  public static void failDownload(Context context) {
    safeCancel(context, DOWNLOAD_NOTIFICATION_ID);
  }
}
