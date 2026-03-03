package com.example.pcloud;

import android.graphics.Bitmap;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class MockPCloudServer {
  private static final BigInteger P = new BigInteger("286134470859861285423767856156329902081");
  private static final int PORT = 22703;

  private volatile boolean running;
  private ServerSocket serverSocket;
  private Thread serverThread;
  private final List<Thread> clientThreads = Collections.synchronizedList(new ArrayList<>());

  private final Map<String, String> users = new ConcurrentHashMap<>();
  private final Map<String, LinkedHashMap<String, String>> albums = new ConcurrentHashMap<>();
  private final List<String> uploadedPhotoNames = Collections.synchronizedList(new ArrayList<>());

  void seedUser(String username, String password) {
    users.put(username, password);
  }

  void start() throws IOException {
    running = true;
    serverSocket = new ServerSocket(PORT);
    serverSocket.setReuseAddress(true);
    serverThread =
        new Thread(
            () -> {
              while (running) {
                try {
                  Socket client = serverSocket.accept();
                  Thread clientThread = new Thread(() -> handleClient(client), "MockPCloudClient");
                  clientThread.start();
                  clientThreads.add(clientThread);
                } catch (IOException ignored) {
                  if (!running) {
                    return;
                  }
                }
              }
            },
            "MockPCloudServer");
    serverThread.start();
  }

  void stop() {
    running = false;
    if (serverSocket != null) {
      try {
        serverSocket.close();
      } catch (IOException ignored) {
      }
    }
    if (serverThread != null) {
      try {
        serverThread.join(2000);
      } catch (InterruptedException ignored) {
      }
    }
    synchronized (clientThreads) {
      for (Thread thread : clientThreads) {
        try {
          thread.join(1000);
        } catch (InterruptedException ignored) {
        }
      }
      clientThreads.clear();
    }
  }

  boolean hasAlbum(String albumName) {
    return albums.containsKey(albumName);
  }

  boolean hasUploadedPhoto(String albumName, String photoName) {
    LinkedHashMap<String, String> photos = albums.get(albumName);
    return photos != null && photos.containsKey(photoName);
  }

  boolean hasPhoto(String albumName, String photoName) {
    LinkedHashMap<String, String> photos = albums.get(albumName);
    return photos != null && photos.containsKey(photoName);
  }

  void seedManyAlbums(int count) {
    for (int i = 0; i < count; i++) {
      String albumName = String.format("album_%03d", i);
      albums.put(albumName, new LinkedHashMap<>());
    }
  }

  void seedAlbumWithLargePhotos(String albumName, int photoCount, int width, int height) {
    LinkedHashMap<String, String> photos = new LinkedHashMap<>();
    String encodedImage = createLargeEncodedImage(width, height);
    for (int i = 0; i < photoCount; i++) {
      String fileName = String.format("large_%03d.jpg", i);
      photos.put(fileName, encodedImage);
    }
    albums.put(albumName, photos);
  }

  private String createLargeEncodedImage(int width, int height) {
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(0xFF5E8BFF);
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream);
    return android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP);
  }

  private void handleClient(Socket client) {
    byte[] aesKey = null;
    try {
      client.setSoTimeout(10000);
      InputStream in = client.getInputStream();
      OutputStream out = client.getOutputStream();

      BigInteger generator = BigInteger.valueOf(5);
      int serverNum = 1337;
      String generatorMessage = buildMessage("GENERATOR", "1", generator.toString(), null);
      out.write(generatorMessage.getBytes(StandardCharsets.UTF_8));
      out.flush();

      ProtocolMessage clientScoreMessage = recvMessage(in, null);
      BigInteger clientScore = new BigInteger(clientScoreMessage.data);
      BigInteger serverScore = generator.modPow(BigInteger.valueOf(serverNum), P);

      String scoreMessage = buildMessage("SCORE", "1", serverScore.toString(), null);
      out.write(scoreMessage.getBytes(StandardCharsets.UTF_8));
      out.flush();

      BigInteger aesKeyNum = clientScore.modPow(BigInteger.valueOf(serverNum), P);
      aesKey = to16ByteKey(aesKeyNum);

      while (running && !client.isClosed()) {
        ProtocolMessage request = recvMessage(in, aesKey);
        if (request == null) {
          break;
        }
        ProtocolMessage response = handleRequest(request);
        if (response != null) {
          String payload = buildMessage(response.name, response.type, response.data, aesKey);
          out.write(payload.getBytes(StandardCharsets.UTF_8));
          out.flush();
        }
      }
    } catch (Exception ignored) {
    } finally {
      try {
        client.close();
      } catch (IOException ignored) {
      }
    }
  }

  private ProtocolMessage handleRequest(ProtocolMessage request) {
    String name = request.name;
    if ("REGISTER".equals(name)) {
      String[] parts = request.data.split("\\n");
      if (parts.length >= 2 && !users.containsKey(parts[0])) {
        users.put(parts[0], parts[1]);
        return new ProtocolMessage("REGISTER", "1", "");
      }
      return new ProtocolMessage("REGISTER", "201", "");
    }
    if ("LOGIN".equals(name)) {
      String[] parts = request.data.split("\\n");
      if (parts.length >= 2 && parts[1].equals(users.get(parts[0]))) {
        return new ProtocolMessage("LOGIN", "1", "");
      }
      return new ProtocolMessage("LOGIN", "200", "");
    }
    if ("ALBUMS".equals(name)) {
      StringBuilder builder = new StringBuilder();
      for (String album : albums.keySet()) {
        if (builder.length() > 0) {
          builder.append("\n");
        }
        builder.append(album);
      }
      return new ProtocolMessage("ALBUMS", "1", builder.toString());
    }
    if ("NEW_ALBUM".equals(name)) {
      if (!albums.containsKey(request.data)) {
        albums.put(request.data, new LinkedHashMap<>());
        return new ProtocolMessage("NEW_ALBUM", "1", "");
      }
      return new ProtocolMessage("NEW_ALBUM", "203", "");
    }
    if ("PHOTOS".equals(name)) {
      LinkedHashMap<String, String> photos = albums.get(request.data);
      if (photos == null) {
        return new ProtocolMessage("PHOTOS", "205", "");
      }
      StringBuilder body = new StringBuilder(request.data);
      for (Map.Entry<String, String> entry : photos.entrySet()) {
        body.append("\n").append(entry.getKey()).append("~").append(entry.getValue());
      }
      return new ProtocolMessage("PHOTOS", "1", body.toString());
    }
    if ("UPLOAD_PHOTO".equals(name)) {
      String[] parts = request.data.split("\\n", 3);
      if (parts.length == 3 && albums.containsKey(parts[0])) {
        albums.get(parts[0]).put(parts[1], parts[2]);
        uploadedPhotoNames.add(parts[1]);
        return new ProtocolMessage("UPLOAD_PHOTO", "1", parts[1] + "~" + parts[2]);
      }
      return new ProtocolMessage("UPLOAD_PHOTO", "206", "");
    }
    if ("PHOTO".equals(name)) {
      String[] parts = request.data.split("\\n", 2);
      if (parts.length == 2 && albums.containsKey(parts[0])) {
        String photoData = albums.get(parts[0]).get(parts[1]);
        if (photoData != null) {
          return new ProtocolMessage("PHOTO", "1", photoData);
        }
      }
      return new ProtocolMessage("PHOTO", "207", "");
    }
    if ("DEL_PHOTOS".equals(name)) {
      String[] lines = request.data.split("\\n");
      if (lines.length >= 2 && albums.containsKey(lines[0])) {
        LinkedHashMap<String, String> photos = albums.get(lines[0]);
        for (int i = 1; i < lines.length; i++) {
          if (lines[i].length() > 0) {
            photos.remove(lines[i]);
          }
        }
        return new ProtocolMessage("DEL_PHOTOS", "1", "");
      }
      return new ProtocolMessage("DEL_PHOTOS", "208", "");
    }
    return null;
  }

  private static String buildMessage(String name, String type, String data, byte[] aesKey) {
    String payload = data == null ? "" : data;
    if (aesKey != null && payload.length() > 0) {
      payload = AESEncryptDecrypt.encrypt(payload, aesKey);
    }
    return "name:"
        + name
        + "\n"
        + "type:"
        + type
        + "\n"
        + "size:"
        + payload.length()
        + "\n\n"
        + "data:"
        + payload;
  }

  private static ProtocolMessage recvMessage(InputStream in, byte[] aesKey) throws IOException {
    String headers = readUntilHeaderEnd(in);
    if (headers == null || headers.isEmpty()) {
      return null;
    }
    String name = readHeader(headers, "name");
    String type = readHeader(headers, "type");
    int size = Integer.parseInt(readHeader(headers, "size"));

    byte[] bodyBytes = readExactly(in, size + 5);
    String body = new String(bodyBytes, StandardCharsets.UTF_8);
    String data = body.startsWith("data:") ? body.substring(5) : "";
    if (aesKey != null && !data.isEmpty()) {
      data = AESEncryptDecrypt.decrypt(data, aesKey);
    }
    return new ProtocolMessage(name, type, data);
  }

  private static String readUntilHeaderEnd(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int prev = -1;
    while (true) {
      int current = in.read();
      if (current == -1) {
        return null;
      }
      out.write(current);
      if (prev == '\n' && current == '\n') {
        return out.toString("UTF-8");
      }
      prev = current;
    }
  }

  private static byte[] readExactly(InputStream in, int length) throws IOException {
    byte[] data = new byte[length];
    int offset = 0;
    while (offset < length) {
      int read = in.read(data, offset, length - offset);
      if (read == -1) {
        throw new IOException("Socket closed while reading body");
      }
      offset += read;
    }
    return data;
  }

  private static String readHeader(String headers, String key) {
    String[] lines = headers.split("\\n");
    for (String line : lines) {
      if (line.startsWith(key + ":")) {
        return line.substring((key + ":").length());
      }
    }
    return "";
  }

  private static byte[] to16ByteKey(BigInteger num) {
    byte[] key = new byte[16];
    BigInteger value = num;
    for (int i = 15; i >= 0; i--) {
      key[i] = value.mod(BigInteger.valueOf(256)).byteValue();
      value = value.divide(BigInteger.valueOf(256));
    }
    return key;
  }

  private static final class ProtocolMessage {
    final String name;
    final String type;
    final String data;

    ProtocolMessage(String name, String type, String data) {
      this.name = name;
      this.type = type;
      this.data = data == null ? "" : data;
    }
  }
}
