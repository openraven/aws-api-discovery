/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.producer.services;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.google.common.collect.ImmutableMap;
import com.segment.analytics.Analytics;
import com.segment.analytics.messages.TrackMessage;
import io.openraven.data.interfaces.Envelope;
import io.openraven.producer.properties.AnalyticsProperties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;

@Component
public class DiscoveryServices {

  private final KafkaOperations<Long, String> producer;

  private final Analytics segmentAnalytics;

  private final boolean sendTelemetry;

  private final boolean synchronousSend;

  private static final Logger LOG = LoggerFactory.getLogger(DiscoveryServices.class);

  private static final ObjectMapper mapper = new ObjectMapper();

  private static final ObjectWriter objectWriter;

  static {
    mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
        ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.WRAPPER_ARRAY).configure(
        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false );
    objectWriter = mapper.writerWithDefaultPrettyPrinter();
  }

  @Autowired
  private DiscoveryServices(final KafkaOperations<Long, String> kafka,
      final AnalyticsProperties serviceProps) {
    this(kafka, serviceProps, true);
  }

  DiscoveryServices(KafkaOperations<Long, String> kafka, AnalyticsProperties serviceProps,
      boolean synchronousSend) {
    this.producer = kafka;
    this.synchronousSend = synchronousSend;
    segmentAnalytics = Analytics.builder(serviceProps.getKey()).build();
    sendTelemetry = serviceProps.getEnabled();
  }

  public String sendToKafka(Envelope envelope, String resourceID) {
    String json = "uninitialized json for resource " + resourceID;
    try {
      json = objectWriter.writeValueAsString(envelope);

      final ListenableFuture<SendResult<Long, String>> future = producer.sendDefault(json);
      if (synchronousSend) {
        final SendResult<Long, String> sent = future.get(10, TimeUnit.SECONDS);
        LOG.info("sent record := {}", sent.getRecordMetadata());
      }
    } catch (JsonProcessingException | ExecutionException | TimeoutException | InterruptedException e) {
      LOG.error("Error processing resource {} with ex:", resourceID, e);
    }
    return json;
  }

  public void flushKafka() {
    producer.flush();
  }

  public void sendAnalyticsEvent(String event, ImmutableMap<String, String> map) {
    LOG.info("Sending telemetry event {} with properties {}", event, map);
    if (sendTelemetry) {
      segmentAnalytics.enqueue(TrackMessage.builder(event).anonymousId("0").properties(map));
    }
  }

}