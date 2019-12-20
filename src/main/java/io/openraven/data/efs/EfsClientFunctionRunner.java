/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.efs;

import io.openraven.data.shared.ClientFunctionRunner;
import io.openraven.producer.properties.RoleArnConfig;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.efs.EfsClient;

@Component
public class EfsClientFunctionRunner extends ClientFunctionRunner<EfsClient> {

	public EfsClientFunctionRunner(RoleArnConfig roleArnConfig) {
		super(roleArnConfig);
	}

	@Override
	public EfsClient createClient(String region, AwsCredentialsProvider credentialsProvider) {
		return EfsClient.builder().credentialsProvider(credentialsProvider).region(Region.of(region)).build();
	}

}
