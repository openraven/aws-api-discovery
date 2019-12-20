/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.rds;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openraven.data.interfaces.Envelope;
import io.openraven.data.interfaces.IDiscovery;
import io.openraven.data.shared.PayloadUtils;
import io.openraven.data.shared.Utils;
import io.openraven.producer.properties.DiscoveryProperties;
import io.openraven.producer.services.DiscoveryServices;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsResponse;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.DBInstance;
import software.amazon.awssdk.services.rds.model.DescribeDbClustersRequest;
import software.amazon.awssdk.services.rds.model.DescribeDbClustersResponse;
import software.amazon.awssdk.services.rds.model.DescribeDbInstancesRequest;
import software.amazon.awssdk.services.rds.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.rds.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.rds.model.ReservedDBInstance;
import software.amazon.awssdk.services.rds.model.Tag;

@Profile("RDS")
@Component
public class RdsDiscovery implements IDiscovery {

  private final RdsClientFunctionRunner clientProvider;

  private final DiscoveryServices discoveryServices;

  private final DiscoveryProperties discoveryProperties;

  private static final Logger LOG = LoggerFactory.getLogger(RdsDiscovery.class);

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  RdsDiscovery(DiscoveryServices discoveryServices, DiscoveryProperties discoveryProps,
      RdsClientFunctionRunner clientProvider) {
    this.discoveryServices = discoveryServices;
    this.clientProvider = clientProvider;
    this.discoveryProperties = discoveryProps;
  }

  /**
   * Executes a discovery run for this resource type. * @param resourceID is an optional (can be
   * null) filter to only run discovery for that resource ID * @return JSON of the discovered
   * resource(s), used only to confirm discovery took place and gathered expected data
   */
  public List<String> run(@Nullable String resourceID, String discoverySession) {
    List<String> jsons = new ArrayList<>();

    for (String region : this.discoveryProperties.getAws()) {
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
      RdsClient client, AwsCredentialsProvider credentialsProvider) {
    LOG.info("Starting discovery in region {}", region);
    String accountId = getAccountId(credentialsProvider, region);
    Stream.concat(client.describeDBInstances().dbInstances().stream(),
        client.describeReservedDBInstances().reservedDBInstances().stream()
            .map(rdb -> getDBInstanceFromReservedDBInstance(client, rdb)).filter(Objects::nonNull))
        .filter(db -> (resourceID == null || db.dbInstanceArn().equalsIgnoreCase(resourceID)))
        .forEach(db -> {
          RDSInstance data = new RDSInstance(region, db);
          data.discoverySessionId = discoverySession;
          data.awsAccountId = accountId;

          discover(client, db, data, credentialsProvider);
          final Envelope envelope = new Envelope(
              this.discoveryProperties.getIntegration(),
              discoverySession, Instant.now().toString(), data);
          String json = discoveryServices.sendToKafka(envelope, data.resourceId);
          jsons.add(json);
        });

    LOG.info("Finished discovery in region {}", region);
  }

  @Nullable
  private DBInstance getDBInstanceFromReservedDBInstance(RdsClient client,
      ReservedDBInstance reservedInstance) {
    List<DBInstance> DBInstances = client.describeDBInstances(DescribeDbInstancesRequest.builder()
        .dbInstanceIdentifier(reservedInstance.reservedDBInstanceArn()).build()).dbInstances();

    // there should only be max one instance to return, as we're filtering by arn
    return DBInstances.stream().findFirst().orElse(null);

  }

  public void discover(RdsClient client, DBInstance resource, final RDSInstance data,
      AwsCredentialsProvider awsCredentialsProvider) {
    LOG.info("Getting DBSize for {}", resource.dbInstanceArn());

    List<Dimension> dimensions = new ArrayList<>();
    dimensions.add(
        Dimension.builder().name("DBInstanceIdentifier").value(resource.dbInstanceIdentifier())
            .build());
    Pair<Long, GetMetricStatisticsResponse> freeStorageSpace = Utils
        .getCloudwatchMetricMinimum(data.awsRegion, "AWS/RDS", "FreeStorageSpace",
            dimensions, awsCredentialsProvider);

    DescribeDbClustersResponse describeDbClustersResponse = client.describeDBClusters(
        DescribeDbClustersRequest.builder().dbClusterIdentifier(resource.dbClusterIdentifier())
            .build());

    LOG.info("Getting tags for {}", resource.dbInstanceArn());
    ListTagsForResourceResponse listTagsForResourceResponse = client.listTagsForResource(
        ListTagsForResourceRequest.builder().resourceName(resource.dbInstanceArn()).build());
    getTags(data, listTagsForResourceResponse);

    data.supplementaryConfiguration = PayloadUtils
        .update(data.supplementaryConfiguration, Map.of("size",Map.of("FreeStorageSpace",
            freeStorageSpace.getValue0())));
    data.supplementaryConfiguration = PayloadUtils
        .update(data.supplementaryConfiguration, describeDbClustersResponse);
  }

  private void getTags(RDSInstance rdsInstance, ListTagsForResourceResponse tags) {
    JsonNode tagsNode = objectMapper
        .convertValue(tags.tagList().stream().collect(
            Collectors.toMap(Tag::key, Tag::value)), JsonNode.class);

    rdsInstance.tags = PayloadUtils.update(rdsInstance.tags, tagsNode);

  }
}
