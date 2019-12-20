/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.efs;

import io.openraven.data.interfaces.Envelope;
import io.openraven.data.interfaces.IDiscovery;
import io.openraven.data.shared.PayloadUtils;
import io.openraven.producer.properties.DiscoveryProperties;
import io.openraven.producer.services.DiscoveryServices;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.efs.EfsClient;
import software.amazon.awssdk.services.efs.model.DescribeMountTargetsRequest;
import software.amazon.awssdk.services.efs.model.DescribeMountTargetsResponse;
import software.amazon.awssdk.services.efs.model.FileSystemDescription;

@Profile("EFS")
@Component
public class EfsDiscovery implements IDiscovery {

  private final EfsClientFunctionRunner clientProvider;

  private final DiscoveryServices discoveryServices;

  private final DiscoveryProperties discoveryProperties;

  private static final Logger LOG = LoggerFactory.getLogger(EfsDiscovery.class);

  @Autowired
  EfsDiscovery(DiscoveryServices discoveryServices, DiscoveryProperties discoveryProps,
      EfsClientFunctionRunner clientProvider) {
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
      EfsClient client, AwsCredentialsProvider awsCredentialsProvider) {
    LOG.info("Starting discovery in region {}", region);

    client.describeFileSystems().fileSystems().stream().filter(fs -> (resourceID == null || String
        .format("arn:aws:elasticfilesystem:%s:%s:file-system/%s", region, fs.ownerId(),
            fs.fileSystemId())
        .equalsIgnoreCase(resourceID))).forEach(fs -> {
      EfsFileSystem data = new EfsFileSystem(region, fs);
      data.discoverySessionId = discoverySession;

      discover(client, fs, data);
      final Envelope envelope = new Envelope(
          this.discoveryProperties.getIntegration(),
          discoverySession, Instant.now().toString(), data);
      String json = discoveryServices.sendToKafka(envelope, data.resourceId);
      jsons.add(json);
    });

    LOG.info("Finished discovery in region {}", region);
  }

  public void discover(EfsClient client, FileSystemDescription resource, EfsFileSystem data) {
    LOG.info("Getting mount points for {}", resource.fileSystemId());

    DescribeMountTargetsResponse describeMountTargetsResponse = client.describeMountTargets(
        DescribeMountTargetsRequest.builder().fileSystemId(resource.fileSystemId()).build());
    data.configuration = PayloadUtils.update(data.configuration, describeMountTargetsResponse);
  }

}
