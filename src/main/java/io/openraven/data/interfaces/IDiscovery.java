/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.interfaces;

import java.util.List;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

public interface IDiscovery {

  List<String> run(String resourceID, String discoverySession);

  default String getAccountId(AwsCredentialsProvider awsCredentialsProvider, String region) {
    try (StsClient stsClient = StsClient.builder().credentialsProvider(awsCredentialsProvider)
        .region(
            Region.of(region))
        .build();) {

      GetCallerIdentityResponse response = stsClient.getCallerIdentity();

      return response.account();
    }
  }

  default String getAnalyticsName() {
    return getClass().getSimpleName();
  }
}
