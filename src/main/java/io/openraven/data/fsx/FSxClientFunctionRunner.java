/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.fsx;

import io.openraven.data.shared.ClientFunctionRunner;
import io.openraven.producer.properties.RoleArnConfig;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.fsx.FSxClient;

@Component
public class FSxClientFunctionRunner extends ClientFunctionRunner<FSxClient> {

	public FSxClientFunctionRunner(RoleArnConfig roleArnConfig) {
		super(roleArnConfig);
	}

	@Override
	public FSxClient createClient(String region, AwsCredentialsProvider credentialsProvider) {
		return FSxClient.builder().credentialsProvider(credentialsProvider).region(Region.of(region)).build();
	}

}
