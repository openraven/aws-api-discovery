/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.ec2;

import io.openraven.data.shared.ClientFunctionRunner;
import io.openraven.producer.properties.RoleArnConfig;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;

@Component
public class Ec2ClientFunctionRunner extends ClientFunctionRunner<Ec2Client> {

	public Ec2ClientFunctionRunner(RoleArnConfig roleArnConfig) {
		super(roleArnConfig);
	}

	@Override
	public Ec2Client createClient(String region, AwsCredentialsProvider credentialsProvider) {
		return Ec2Client.builder().credentialsProvider(credentialsProvider).region(Region.of(region)).build();
	}

}
