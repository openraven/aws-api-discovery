/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.redshift;

import io.openraven.data.shared.ClientFunctionRunner;
import io.openraven.producer.properties.RoleArnConfig;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.redshift.RedshiftClient;

@Component
public class RedshiftClientProvider extends ClientFunctionRunner<RedshiftClient> {

	public RedshiftClientProvider(RoleArnConfig roleArnConfig) {
		super(roleArnConfig);
	}

	@Override
	protected RedshiftClient createClient(String region, AwsCredentialsProvider credentialsProvider) {
		return RedshiftClient.builder().credentialsProvider(credentialsProvider).region(Region.of(region)).build();
	}

}
