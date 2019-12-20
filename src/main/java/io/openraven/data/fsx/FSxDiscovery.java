/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.fsx;

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
import software.amazon.awssdk.services.fsx.FSxClient;
import software.amazon.awssdk.services.fsx.model.FileSystem;

@Profile("FSX")
@Component
public class FSxDiscovery implements IDiscovery {

  private final FSxClientFunctionRunner clientProvider;

  private final DiscoveryServices discoveryServices;

  private final DiscoveryProperties discoveryProperties;

  private static final Logger LOG = LoggerFactory.getLogger(FSxDiscovery.class);

  @Autowired
  FSxDiscovery(DiscoveryServices discoveryServices, DiscoveryProperties discoveryProps,
      FSxClientFunctionRunner clientProvider) {
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

  public void doRun(@Nullable String resourceID, String discoverySession, List<String> jsons,
      String region,
      FSxClient client, AwsCredentialsProvider awsCredentialsProvider) {
    LOG.info("Starting discovery in region {}", region);
    String accountId = getAccountId(awsCredentialsProvider, region);
    client.describeFileSystems().fileSystems().stream()
        .filter(fs -> (resourceID == null || fs.resourceARN().equalsIgnoreCase(resourceID)))
        .forEach(fs -> {
          FSxFileSystem data = new FSxFileSystem(region, fs);
          data.awsAccountId = accountId;
          data.discoverySessionId = discoverySession;

          discover(client, fs, data, awsCredentialsProvider);
          final Envelope envelope = new Envelope(
              this.discoveryProperties.getIntegration(),
              discoverySession, Instant.now().toString(), data);
          String json = discoveryServices.sendToKafka(envelope, data.resourceId);
          jsons.add(json);
        });

    LOG.info("Finished discovery in region {}", region);
  }

  public void discover(FSxClient client, FileSystem resource, FSxFileSystem data,
      AwsCredentialsProvider awsCredentialsProvider) {
    LOG.info("Getting Size for {}", resource.resourceARN());

    List<Dimension> dimensions = new ArrayList<>();
    dimensions
        .add(Dimension.builder().name("FileSystemId").value(resource.fileSystemId()).build());
    Pair<Long, GetMetricStatisticsResponse> freeStorageCapacity = Utils
        .getCloudwatchMetricMinimum(data.awsRegion, "AWS/FSx",
            "FreeStorageCapacity", dimensions, awsCredentialsProvider);

    data.supplementaryConfiguration = PayloadUtils
        .update(data.supplementaryConfiguration,
            Map.of("FreeStorageCapacity", freeStorageCapacity.getValue0()));
  }

}
