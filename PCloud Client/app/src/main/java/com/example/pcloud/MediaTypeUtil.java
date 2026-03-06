package com.example.pcloud;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import java.util.Locale;

final class MediaTypeUtil {
  private MediaTypeUtil() {}

  static boolean isGifFileName(String fileName) {
    if (fileName == null || fileName.trim().equals("")) {
      return false;
    }
    return fileName.toLowerCase(Locale.ROOT).endsWith(".gif");
  }

  static boolean isVideoFileName(String fileName) {
    if (fileName == null || fileName.trim().equals("")) {
      return false;
    }
    String lower = fileName.toLowerCase(Locale.ROOT);
    return lower.endsWith(".mp4")
        || lower.endsWith(".mkv")
        || lower.endsWith(".webm")
        || lower.endsWith(".3gp")
        || lower.endsWith(".mov")
        || lower.endsWith(".avi")
        || lower.endsWith(".m4v");
  }

  static String detectMimeType(String fileName) {
    if (isGifFileName(fileName)) {
      return "image/gif";
    }
    if (isVideoFileName(fileName)) {
      String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
      if (lower.endsWith(".webm")) {
        return "video/webm";
      }
      if (lower.endsWith(".3gp")) {
        return "video/3gpp";
      }
      if (lower.endsWith(".mkv")) {
        return "video/x-matroska";
      }
      if (lower.endsWith(".mov")) {
        return "video/quicktime";
      }
      if (lower.endsWith(".avi")) {
        return "video/x-msvideo";
      }
      if (lower.endsWith(".m4v")) {
        return "video/x-m4v";
      }
      return "video/mp4";
    }
    return "image/jpeg";
  }

  static Bitmap createVideoPlaceholder(int width, int height) {
    int outWidth = Math.max(64, width);
    int outHeight = Math.max(64, height);
    Bitmap bitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    canvas.drawColor(0xFF2F2F2F);

    float triangleSize = Math.min(outWidth, outHeight) * 0.30f;
    float centerX = outWidth / 2.0f;
    float centerY = outHeight / 2.0f;

    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(Color.WHITE);

    android.graphics.Path path = new android.graphics.Path();
    path.moveTo(centerX - triangleSize * 0.45f, centerY - triangleSize * 0.6f);
    path.lineTo(centerX - triangleSize * 0.45f, centerY + triangleSize * 0.6f);
    path.lineTo(centerX + triangleSize * 0.65f, centerY);
    path.close();
    canvas.drawPath(path, paint);

    return bitmap;
  }

  static String formatDurationMillis(long durationMillis) {
    if (durationMillis <= 0) {
      return "";
    }
    long totalSeconds = durationMillis / 1000L;
    long seconds = totalSeconds % 60L;
    long minutes = (totalSeconds / 60L) % 60L;
    long hours = totalSeconds / 3600L;
    if (hours > 0) {
      return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
    }
    return String.format(Locale.US, "%d:%02d", minutes, seconds);
  }
}
