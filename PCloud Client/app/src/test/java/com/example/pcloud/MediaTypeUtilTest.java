package com.example.pcloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MediaTypeUtilTest {
  @Test
  public void detectsGifAndVideoByExtension() {
    assertTrue(MediaTypeUtil.isGifFileName("anim.GIF"));
    assertTrue(MediaTypeUtil.isVideoFileName("clip.mp4"));
    assertTrue(MediaTypeUtil.isVideoFileName("clip.MOV"));
    assertFalse(MediaTypeUtil.isVideoFileName("photo.jpg"));
  }

  @Test
  public void detectsMimeType() {
    assertEquals("image/gif", MediaTypeUtil.detectMimeType("x.gif"));
    assertEquals("video/mp4", MediaTypeUtil.detectMimeType("x.mp4"));
    assertEquals("video/webm", MediaTypeUtil.detectMimeType("x.webm"));
    assertEquals("image/jpeg", MediaTypeUtil.detectMimeType("x.jpg"));
  }
}
