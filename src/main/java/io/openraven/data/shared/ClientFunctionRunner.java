/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.shared;

import static org.springframework.util.CollectionUtils.isEmpty;

import io.openraven.producer.properties.RoleArnConfig;
import io.sentry.Sentry;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

public abstract class ClientFunctionRunner<CLIENT extends SdkClient> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClientFunctionRunner.class);
  protected final RoleArnConfig roleArnConfig;
  private final StsClient stsClient;

  public ClientFunctionRunner(RoleArnConfig roleArnConfig) {
    this.roleArnConfig = roleArnConfig;
    stsClient = StsClient.create();
  }

  /**
   * @param region   the {@link Region} compatible string which will be used to create a {@code
   *                 client} instance
   * @param consumer the code to run with the created {@code client}, which will be invoked {@code
   *                 length(roleArns)+1} times, and you don't have to close the client instance
   *                 because I will do that
   */
  public void applyClient(String region, BiConsumer<CLIENT, AwsCredentialsProvider> consumer) {
    // We should rely on the same credential provider we use for non cross account
    // discovery, DefaultCredentialsProvider
    try (final var provider = DefaultCredentialsProvider.create();
        final CLIENT client = createClient(region, provider)) {
      consumer.accept(client, provider);
    } catch (Exception e) {
      LOGGER.error("Unable to run \"local\" discovery in Region {}", region, e);
      Sentry.capture(e);
    }

    final List<String> roleArns = roleArnConfig.getRoleArns();
    // don't bother creating the StsClient if there's nothing to do with it
    // since that's only a way for things to fail needlessly
    if (isEmpty(roleArns)) {
      return;
    }
    LOGGER.debug("Applying client in Region {} to Role ARNs: {}", region, roleArns);
    for (String roleArn : roleArns) {
      try (final var provider = StsAssumeRoleCredentialsProvider.builder()
          .stsClient(stsClient)
          .refreshRequest(
              AssumeRoleRequest.builder()
                  .roleArn(roleArn)
                  .roleSessionName(UUID.randomUUID().toString())
                  .build()
          ).build();
          final CLIENT client = createClient(region, provider)
      ) {
        // We need the pass along the credential provider for one off client creation
        // in the same role_arn during cross account discovery

        consumer.accept(client, provider);
      } catch (Exception e) {
        LOGGER.error("Unable to apply client in Region {} to Role ARN \"{}\"", region, roleArn, e);
        Sentry.capture(e);
      }
    }
  }

  /**
   * Returns a fresh {@link SdkClient} that <b>YOU MUST CLOSE</b>.
   */
  protected abstract CLIENT createClient(String region, AwsCredentialsProvider credentialsProvider);
}
