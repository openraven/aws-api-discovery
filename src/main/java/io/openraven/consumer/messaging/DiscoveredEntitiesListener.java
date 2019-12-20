/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.consumer.messaging;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import io.openraven.consumer.ElasticSearchOperations;
import io.openraven.data.interfaces.Envelope;
import io.sentry.Sentry;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.stereotype.Component;

@Component
@Profile("consumer")
public class DiscoveredEntitiesListener {

  private static final Logger LOG = LoggerFactory.getLogger(DiscoveredEntitiesListener.class);

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
          ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.WRAPPER_ARRAY);

  private final KafkaOperations<Long, String> deadLetterOperations;
  private final ElasticSearchOperations elasticSearchOperations;

  public DiscoveredEntitiesListener(final KafkaOperations<Long, String> deadLetterOperations,
      ElasticSearchOperations elasticSearchOperations) {
    this.deadLetterOperations = deadLetterOperations;
    this.elasticSearchOperations = elasticSearchOperations;
  }

  @KafkaListener(id = "discoveredEntitiesListener", groupId = "${openraven.app.v1.kafka.consumer.group}",
      topics = "${openraven.app.v1.kafka.consumer.topic}")
  public void onMessage(final ConsumerRecord<Long, String> record) {
    final Envelope dataEnvelope;
    final String recordValue = record.value();
    String entityId;
    try {
      dataEnvelope = MAPPER.readValue(recordValue, Envelope.class);
      entityId = dataEnvelope.contents.arn;

    } catch (IOException e) {
      LOG.warn("Bogus Envelope JSON: '{}'", recordValue, e);
      sendToDeadLetterQueue(recordValue);
      return;
    }

    try {
      LOG.info("Received Record of type {} in discovery session {}",
          dataEnvelope.contents.resourceType,
          dataEnvelope.discoverySession);

      final boolean saveOK = elasticSearchOperations.save(dataEnvelope.contents);
      if (saveOK) {
        LOG.info("Saved resource id {} in discovery session {}", entityId,
            dataEnvelope.discoverySession);
      } else {
        LOG.error("Was unable to save resource with id {} in discovery session {}", entityId,
            dataEnvelope.discoverySession);
        sendToDeadLetterQueue(recordValue);
      }

    } catch (Exception ex) {
      LOG.error("Was unable to save resource with id {} in discovery session {}", entityId,
          dataEnvelope.discoverySession, ex);
      sendToDeadLetterQueue(recordValue);
    }
  }

  /**
   * Attempts to deliver to the DLQ, logging the exception if unable to do so within 10 seconds.
   */
  private void sendToDeadLetterQueue(String data) {
    try {
      deadLetterOperations.sendDefault(data).get(10, TimeUnit.SECONDS);
    } catch (Exception e) {
      LOG.error("Unable to deliver to DLQ while handling deserialization exception", e);
      Sentry.capture(e);
    }
  }

}
