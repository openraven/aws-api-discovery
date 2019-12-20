/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.shared;

import io.openraven.producer.properties.RoleArnConfig;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;

@Component
public class CloudwatchClientFunctionRunner extends ClientFunctionRunner<CloudWatchClient> {

	public CloudwatchClientFunctionRunner(RoleArnConfig roleArnConfig) {
		super(roleArnConfig);
	}

	@Override
	protected CloudWatchClient createClient(String region, AwsCredentialsProvider credentialsProvider) {
		return CloudWatchClient.builder().credentialsProvider(credentialsProvider).region(Region.of(region)).build();
	}

}
