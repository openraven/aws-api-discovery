/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.redshift;

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
import software.amazon.awssdk.services.redshift.RedshiftClient;
import software.amazon.awssdk.services.redshift.model.Cluster;
import software.amazon.awssdk.services.redshift.model.DescribeStorageResponse;

@Profile("RSH")
@Component
public class RedshiftDiscovery implements IDiscovery {

  private final RedshiftClientProvider clientProvider;

  private final DiscoveryServices discoveryServices;

  private final DiscoveryProperties discoveryProperties;

  private static final Logger LOG = LoggerFactory.getLogger(RedshiftDiscovery.class);

  @Autowired
  RedshiftDiscovery(DiscoveryServices discoveryServices, DiscoveryProperties discoveryProps,
      RedshiftClientProvider clientProvider) {
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
      RedshiftClient client, AwsCredentialsProvider credentialsProvider) {
    LOG.info("Starting discovery in region {}", region);

    client.describeClusters().clusters().stream()
        .filter(cluster -> (resourceID == null || String
            .format("arn:aws:redshift:%s:%s:cluster:%s", region,
                getAccountId(credentialsProvider, region), cluster.clusterIdentifier())
            .equalsIgnoreCase(resourceID)))
        .forEach(cluster -> {
          RedshiftCluster data = new RedshiftCluster(region, cluster,
              getAccountId(credentialsProvider, region));
          data.discoverySessionId = discoverySession;

          discover(client, cluster, data, credentialsProvider);
          final Envelope envelope = new Envelope(
              discoveryProperties.getIntegration(),
              discoverySession, Instant.now().toString(), data);
          String json = discoveryServices.sendToKafka(envelope, data.resourceId);
          jsons.add(json);
        });

    LOG.info("Finished discovery in region {}", region);
  }

  public void discover(RedshiftClient client, Cluster resource, RedshiftCluster data,
      AwsCredentialsProvider awsCredentialsProvider) {
    DescribeStorageResponse describeStorageResponse = client.describeStorage();

    List<Dimension> dimensions = new ArrayList<>();
    dimensions.add(
        Dimension.builder().name("ClusterIdentifier").value(resource.clusterIdentifier())
            .build());
    Pair<Double, GetMetricStatisticsResponse> percentageDiskSpaceUsed = Utils
        .getCloudwatchDoubleMetricMinimum(data.awsRegion, "AWS/Redshift",
            "PercentageDiskSpaceUsed", dimensions, awsCredentialsProvider);

    data.supplementaryConfiguration = PayloadUtils.update(data.supplementaryConfiguration,
        Map.of("PercentageDiskSpaceUsed", percentageDiskSpaceUsed.getValue0()));
    data.supplementaryConfiguration = PayloadUtils
        .update(data.supplementaryConfiguration, describeStorageResponse);
  }
}
