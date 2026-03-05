package com.example.pcloud;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

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
              CHANNEL_ID,
              "PCloud Transfers",
              NotificationManager.IMPORTANCE_LOW);
      channel.setDescription("Upload and download progress");
      NotificationManager manager =
          (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
      if (manager != null) {
        manager.createNotificationChannel(channel);
      }
    }
    channelCreated = true;
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
      builder
          .setContentText(safeDone + "/" + total + " parts")
          .setProgress(total, safeDone, false);
    } else {
      builder.setContentText("Preparing upload").setProgress(0, 0, true);
    }

    NotificationManagerCompat.from(context).notify(UPLOAD_NOTIFICATION_ID, builder.build());
  }

  public static void completeUpload(Context context) {
    ensureChannel(context);
    NotificationCompat.Builder builder =
        new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Upload complete")
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setOngoing(false)
            .setProgress(0, 0, false)
            .setPriority(NotificationCompat.PRIORITY_LOW);
    NotificationManagerCompat.from(context).notify(UPLOAD_NOTIFICATION_ID, builder.build());
    NotificationManagerCompat.from(context).cancel(UPLOAD_NOTIFICATION_ID);
  }

  public static void failUpload(Context context) {
    ensureChannel(context);
    NotificationCompat.Builder builder =
        new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Upload failed")
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_LOW);
    NotificationManagerCompat.from(context).notify(UPLOAD_NOTIFICATION_ID, builder.build());
    NotificationManagerCompat.from(context).cancel(UPLOAD_NOTIFICATION_ID);
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
      builder
          .setContentText(safeDone + "/" + total + " parts")
          .setProgress(total, safeDone, false);
    } else {
      builder.setContentText("Preparing download").setProgress(0, 0, true);
    }

    NotificationManagerCompat.from(context).notify(DOWNLOAD_NOTIFICATION_ID, builder.build());
  }

  public static void completeDownload(Context context) {
    ensureChannel(context);
    NotificationCompat.Builder builder =
        new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setOngoing(false)
            .setProgress(0, 0, false)
            .setPriority(NotificationCompat.PRIORITY_LOW);
    NotificationManagerCompat.from(context).notify(DOWNLOAD_NOTIFICATION_ID, builder.build());
    NotificationManagerCompat.from(context).cancel(DOWNLOAD_NOTIFICATION_ID);
  }

  public static void failDownload(Context context) {
    ensureChannel(context);
    NotificationCompat.Builder builder =
        new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed")
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_LOW);
    NotificationManagerCompat.from(context).notify(DOWNLOAD_NOTIFICATION_ID, builder.build());
    NotificationManagerCompat.from(context).cancel(DOWNLOAD_NOTIFICATION_ID);
  }
}
