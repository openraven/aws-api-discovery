/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.consumer;

import static org.slf4j.LoggerFactory.getLogger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openraven.consumer.services.ElasticSearchService;
import io.openraven.consumer.utils.EncodedNamedUUIDGenerator;
import io.openraven.data.interfaces.AWSResource;
import java.io.IOException;
import java.time.Instant;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class ElasticSearchOperations {

  private final ElasticSearchService elasticSearchService;
  private final ObjectMapper json;
  private final Logger log = getLogger(ElasticSearchOperations.class);

  public ElasticSearchOperations(final ElasticSearchService elasticSearchService,
      final ObjectMapper json) {

    this.elasticSearchService = elasticSearchService;
    this.json = json;
  }

  public boolean save(AWSResource resource) {
    String indexName = resource.resourceType.replace(":", "").toLowerCase();
    String arn = resource.arn.toLowerCase();

    try {
      resource.documentId = EncodedNamedUUIDGenerator.getEncodedNamedUUID(arn);
      resource.updatedIso = Instant.now().toString();
      return elasticSearchService
          .writeDocument(indexName, resource.documentId,
              json.convertValue(resource, ObjectNode.class))
          != null;
    } catch (IOException e) {
      log.error("Failure attempting to write {} with message {}", arn, e.getMessage());
      return false;
    }
  }
}
