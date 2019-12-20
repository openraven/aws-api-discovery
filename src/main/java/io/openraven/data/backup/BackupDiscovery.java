/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.backup;

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
import software.amazon.awssdk.services.backup.BackupClient;
import software.amazon.awssdk.services.backup.model.BackupVaultListMember;
import software.amazon.awssdk.services.backup.model.ListTagsRequest;
import software.amazon.awssdk.services.backup.model.ListTagsResponse;

@Profile("BACKUP")
@Component
public class BackupDiscovery implements IDiscovery {

  private final BackupClientFunctionRunner clientProvider;

  private final DiscoveryServices discoveryServices;

  private final DiscoveryProperties discoveryProperties;

  private static final Logger LOG = LoggerFactory.getLogger(BackupDiscovery.class);

  @Autowired
  BackupDiscovery(DiscoveryServices discoveryServices, DiscoveryProperties discoveryProps,
      BackupClientFunctionRunner clientProvider) {
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
      BackupClient client, AwsCredentialsProvider credentialsProvider) {
    LOG.info("Starting discovery in region {}", region);
    final String accountId = getAccountId(credentialsProvider, region);
    client.listBackupVaults().backupVaultList().stream()
        .filter(
            vault -> (resourceID == null || vault.backupVaultArn().equalsIgnoreCase(resourceID)))
        .forEach(backupVaultListMember -> {
          BackupVault data = new BackupVault(backupVaultListMember);
          data.awsAccountId = accountId;
          data.awsRegion = region;
          data.discoverySessionId = discoverySession;

          discover(client, backupVaultListMember, data);

          final String id = backupVaultListMember.backupVaultArn();
          final Envelope envelope = new Envelope(
              discoveryProperties.getIntegration(),
              discoverySession, Instant.now().toString(),
              data);
          String json = discoveryServices.sendToKafka(envelope, id);
          jsons.add(json);
        });

    LOG.info("Finished discovery in region {}", region);
  }

  public void discover(BackupClient client, BackupVaultListMember resource, BackupVault data) {
    ListTagsResponse listTagsResponse = client
        .listTags(ListTagsRequest.builder().resourceArn(resource.backupVaultArn()).build());
    data.configuration = PayloadUtils.update(data.configuration, listTagsResponse);

  }
}
