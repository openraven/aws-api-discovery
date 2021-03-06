/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.consumer.utils;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;

/**
 * Inspired by https://github.com/elastic/elasticsearch/blob/c610ef2955d2d147503968434879dfaf6aa24177/server/src/main/java/org/elasticsearch/common/TimeBasedUUIDGenerator.java
 */
public class EncodedNamedUUIDGenerator {

  public static String getEncodedNamedUUID(String target) throws UnsupportedEncodingException {
    if (target.isEmpty()) {
      throw new IllegalStateException("Target for Encoded Named UUID is empty.");
    }

    byte[] targetBytes = target.getBytes();
    UUID uuid = UUID.nameUUIDFromBytes(targetBytes);

    ByteBuffer uuidBytes = ByteBuffer.wrap(new byte[16]);
    uuidBytes.putLong(uuid.getMostSignificantBits());
    uuidBytes.putLong(uuid.getLeastSignificantBits());

    return Base64.getUrlEncoder().withoutPadding().encodeToString(uuidBytes.array());
  }

}
