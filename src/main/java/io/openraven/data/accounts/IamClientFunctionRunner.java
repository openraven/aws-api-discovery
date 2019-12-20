/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.accounts;

import io.openraven.data.shared.ClientFunctionRunner;
import io.openraven.producer.properties.RoleArnConfig;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;

@Component
public class IamClientFunctionRunner extends ClientFunctionRunner<IamClient> {

    public IamClientFunctionRunner(RoleArnConfig roleArnConfig) {
        super(roleArnConfig);
    }

    @Override
    public IamClient createClient(String region, AwsCredentialsProvider credentialsProvider) {
        return IamClient.builder().region(Region.AWS_GLOBAL).credentialsProvider(credentialsProvider).build();
    }

}
