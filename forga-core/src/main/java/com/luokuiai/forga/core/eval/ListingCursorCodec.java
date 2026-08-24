package com.luokuiai.forga.core.eval;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class ListingCursorCodec {

  private static final int KEY_BITS = 256;

  private static final int NONCE_BYTES = 12;

  private static final int TAG_BITS = 128;

  private final SecretKey key;

  private final SecureRandom random;

  ListingCursorCodec() {
    random = new SecureRandom();
    try {
      KeyGenerator generator = KeyGenerator.getInstance("AES");
      generator.init(KEY_BITS, random);
      key = generator.generateKey();
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("AES cursor encryption is unavailable", exception);
    }
  }

  ListObjectsCursor encode(String payload) {
    byte[] nonce = new byte[NONCE_BYTES];
    random.nextBytes(nonce);
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
      byte[] ciphertext = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      byte[] token = ByteBuffer.allocate(nonce.length + ciphertext.length)
          .put(nonce)
          .put(ciphertext)
          .array();
      return new ListObjectsCursor(Base64.getUrlEncoder().withoutPadding().encodeToString(token));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("failed to encode listing cursor", exception);
    }
  }

  Optional<String> decode(ListObjectsCursor cursor) {
    try {
      byte[] token = Base64.getUrlDecoder().decode(cursor.token());
      if (token.length <= NONCE_BYTES) {
        return Optional.empty();
      }
      ByteBuffer buffer = ByteBuffer.wrap(token);
      byte[] nonce = new byte[NONCE_BYTES];
      buffer.get(nonce);
      byte[] ciphertext = new byte[buffer.remaining()];
      buffer.get(ciphertext);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
      return Optional.of(new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8));
    } catch (GeneralSecurityException | IllegalArgumentException exception) {
      return Optional.empty();
    }
  }
}
