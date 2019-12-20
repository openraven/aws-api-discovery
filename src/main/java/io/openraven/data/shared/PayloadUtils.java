/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.shared;

import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openraven.consumer.utils.JacksonMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import software.amazon.awssdk.utils.builder.ToCopyableBuilder;

public class PayloadUtils {

  private static final Logger LOG = LoggerFactory.getLogger(PayloadUtils.class);

  private static final ObjectMapper objectMapper = JacksonMapper.getJacksonMapper();

  @SuppressWarnings("rawtypes")
  public static JsonNode update(@Nullable JsonNode payload, ToCopyableBuilder... responsesToAdd) {
    for (ToCopyableBuilder responseToAdd : responsesToAdd) {
      if (responseToAdd != null) {
        JsonNode jsonNode = objectMapper.convertValue(responseToAdd.toBuilder(), JsonNode.class);
        payload = update(payload, jsonNode);
      }
    }
    return payload;
  }

  @SuppressWarnings("rawtypes")
  public static JsonNode update(@Nullable JsonNode payload,
      Map<String, Object> mappedResponsesToAdd) {

    for (Entry<String, Object> responseToAdd : mappedResponsesToAdd.entrySet()) {
      ObjectNode nodeToAdd = objectMapper.createObjectNode();

      if (responseToAdd.getValue() instanceof ToCopyableBuilder) {
        nodeToAdd.set(responseToAdd.getKey(),
            objectMapper.convertValue(((ToCopyableBuilder) responseToAdd.getValue()).toBuilder(),
                JsonNode.class));
      } else {
        nodeToAdd.set(responseToAdd.getKey(),
            objectMapper.convertValue(responseToAdd.getValue(), JsonNode.class));
      }

      payload = update(payload, nodeToAdd);
    }

    return payload;
  }

  public static JsonNode update(@Nullable JsonNode payload, JsonNode... nodesToAdd) {
    for (JsonNode nodeToAdd : nodesToAdd) {
      if (nodeToAdd != null) {
        try {
          if (payload != null) {
            payload = objectMapper.readerForUpdating(payload).readValue(nodeToAdd);
          } else {
            payload = nodeToAdd;
          }
        } catch (IOException e) {
          LOG.warn("Unable to add extra data {}", nodeToAdd, e);
        }
      }
    }

    return payload;
  }

  @SuppressWarnings("rawtypes")
  public static JsonNode update(ToCopyableBuilder... responsesToAdd) {
    return update(null, responsesToAdd);
  }

  public static JsonNode add(List<? extends ToCopyableBuilder> responsesToAdd) {
    List<JsonNode> tags = responsesToAdd.stream()
        .map((val) -> objectMapper.convertValue(val.toBuilder(), JsonNode.class))
        .collect(toList());

    ArrayNode payload = objectMapper.createArrayNode();
    payload.addAll(tags);

    return payload;
  }
}
