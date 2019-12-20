/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.dynamodb;

import io.openraven.data.shared.ClientFunctionRunner;
import io.openraven.producer.properties.RoleArnConfig;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Component
public class DynamoDbClientFunctionRunner extends ClientFunctionRunner<DynamoDbClient> {

  public DynamoDbClientFunctionRunner(RoleArnConfig roleArnConfig) {
    super(roleArnConfig);
  }

  @Override
  protected DynamoDbClient createClient(String region, AwsCredentialsProvider credentialsProvider) {
    return DynamoDbClient.builder().credentialsProvider(credentialsProvider).region(Region.of(region)).build();
  }

}
