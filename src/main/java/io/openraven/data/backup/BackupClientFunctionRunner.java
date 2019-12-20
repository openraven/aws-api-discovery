/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.backup;

import io.openraven.data.shared.ClientFunctionRunner;
import io.openraven.producer.properties.RoleArnConfig;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.backup.BackupClient;

@Profile("BACKUP")
@Component
public class BackupClientFunctionRunner extends ClientFunctionRunner<BackupClient> {

	public BackupClientFunctionRunner(RoleArnConfig roleArnConfig) {
		super(roleArnConfig);
	}

	@Override
	protected BackupClient createClient(String region, AwsCredentialsProvider credentialsProvider) {
		return BackupClient.builder().credentialsProvider(credentialsProvider).region(Region.of(region)).build();
	}

}
