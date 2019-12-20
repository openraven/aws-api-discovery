/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.ess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openraven.data.interfaces.Envelope;
import io.openraven.data.interfaces.IDiscovery;
import io.openraven.data.shared.PayloadUtils;
import io.openraven.producer.properties.DiscoveryProperties;
import io.openraven.producer.services.DiscoveryServices;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.elasticsearch.ElasticsearchClient;
import software.amazon.awssdk.services.elasticsearch.model.DescribeElasticsearchDomainRequest;
import software.amazon.awssdk.services.elasticsearch.model.ElasticsearchDomainStatus;
import software.amazon.awssdk.services.elasticsearch.model.ListTagsRequest;
import software.amazon.awssdk.services.elasticsearch.model.ListTagsResponse;

@Profile("ESS")
@Component
public class EssDiscovery implements IDiscovery {

  private final EssClientFunctionRunner clientProvider;

  private final DiscoveryServices discoveryServices;

  private final DiscoveryProperties discoveryProperties;

  private static final Logger LOG = LoggerFactory.getLogger(EssDiscovery.class);

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  EssDiscovery(DiscoveryServices discoveryServices, DiscoveryProperties discoveryProps,
      EssClientFunctionRunner clientProvider) {
    this.discoveryServices = discoveryServices;
    this.clientProvider = clientProvider;
    this.discoveryProperties = discoveryProps;
  }

  @Override
  public List<String> run(String resourceID, String discoverySession) {
    List<String> jsons = new ArrayList<>();

    for (String region : discoveryProperties.getAws()) {
      try {
        clientProvider.applyClient(region,
            (client, credentialsProvider) -> doRun(resourceID, discoverySession,
                jsons, region, client, credentialsProvider));
      } catch (SdkServiceException e) {
        LOG.error(String.format("Failed for region %s", region), e);
      }
    }

    return jsons;
  }

  private void doRun(@Nullable String resourceID, String discoverySession, List<String> jsons,
      String region,
      ElasticsearchClient client, AwsCredentialsProvider credentialsProvider) {
    LOG.info("Starting discovery in region {}", region);
    String accountId = getAccountId(credentialsProvider, region);

    client.listDomainNames().domainNames().forEach(domainInfo -> {
      ElasticsearchDomainStatus domainStatus = client
          .describeElasticsearchDomain(
              DescribeElasticsearchDomainRequest.builder().domainName(domainInfo.domainName())
                  .build())
          .domainStatus();
      EssDomain data = new EssDomain(region, domainStatus);
      data.awsAccountId = accountId;
      data.discoverySessionId = discoverySession;

      ListTagsResponse domainTags = client.listTags(builder -> builder.arn(domainStatus.arn()));
      getTags(data, domainTags);

      discover(client, domainStatus, data);
      final Envelope envelope = new Envelope(discoveryProperties.getIntegration(),
          discoverySession,
          Instant.now().toString(), data);
      String json = discoveryServices.sendToKafka(envelope, data.resourceId);
      jsons.add(json);

    });

    LOG.info("Finished discovery in region {}", region);
  }

  public void discover(ElasticsearchClient client, ElasticsearchDomainStatus resource,
      EssDomain data) {
    ListTagsResponse listTagsResponse = client.listTags(
        ListTagsRequest.builder().arn(resource.arn()).build());

    data.configuration = PayloadUtils.update(data.configuration, listTagsResponse);
  }

  private void getTags(EssDomain essDomain, ListTagsResponse tags) {
    JsonNode tagsNode = objectMapper
        .convertValue(tags.tagList().stream().collect(
            Collectors.toMap(t -> t.key(), t -> t.value())), JsonNode.class);

    essDomain.tags = PayloadUtils.update(essDomain.tags, tagsNode);
  }
}
