package com.example.pcloud;

import android.util.Base64;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class AESEncryptDecrypt {
  /**
   * encrypting a String with AES128
   *
   * @param input String text
   * @param key byte array of the key to the AES encryption / decryption
   * @return base64 encoded String of the encrypted text
   */
  public static String encrypt(String input, byte[] key) {
    byte[] crypted = null;
    try {
      StringBuilder inputBuilder = new StringBuilder(input);
      while (inputBuilder.length() % 16 != 0) {
        inputBuilder.append((char) 0);
      }
      input = inputBuilder.toString();

      SecretKeySpec skey = new SecretKeySpec(key, "AES");

      Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, skey);
      crypted = cipher.doFinal(input.getBytes());
      return Base64.encodeToString(crypted, Base64.NO_WRAP);

    } catch (NoSuchAlgorithmException
        | InvalidKeyException
        | NoSuchPaddingException
        | BadPaddingException
        | IllegalBlockSizeException e) {
      e.printStackTrace();
    }

    return null;
  }

  /**
   * decrypting a String with AES128
   *
   * @param input base64 encoded String of the encrypted text
   * @param key byte array of the key to the AES encryption / decryption
   * @return a String of the decrypted text
   */
  public static String decrypt(String input, byte[] key) {
    if (!input.equals("")) {
      byte[] output = null;
      SecretKeySpec skey = new SecretKeySpec(key, "AES");

      try {
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, (Key) skey);
        output = cipher.doFinal(Base64.decode(input, Base64.NO_WRAP));
        output = subNulls(output);
        return new String(output);
      } catch (NoSuchAlgorithmException
          | IllegalBlockSizeException
          | NoSuchPaddingException
          | BadPaddingException
          | InvalidKeyException e) {
        e.printStackTrace();
      }

      return null;
    }
    return "";
  }

  /**
   * sub nulls from the end of the block
   *
   * @param block the block of the decrypted data
   * @return block with no nulls at the end
   */
  private static byte[] subNulls(byte[] block) {
    String o = new String(block);
    if (block[block.length - 1] == (byte) 0) {
      int cnt = 0;
      for (int i = block.length - 1; i >= 0; i--) {
        if (block[i] == (byte) 0) cnt++;
        else break;
      }
      byte[] newOutput = new byte[block.length - cnt];
      for (int i = 0; i < newOutput.length; i++) {
        newOutput[i] = block[i];
      }
      return newOutput;
    }
    return block;
  }
}
