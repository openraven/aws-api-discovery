/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.accounts;

import io.openraven.data.interfaces.Envelope;
import io.openraven.data.interfaces.IDiscovery;
import io.openraven.producer.properties.DiscoveryProperties;
import io.openraven.producer.services.DiscoveryServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;

@Profile("Accounts")
@Component
public class AccountDiscovery implements IDiscovery {

    private final IamClientFunctionRunner clientProvider;

    private final DiscoveryServices discoveryServices;

    private final DiscoveryProperties discoveryProperties;

    private static final Logger LOG = LoggerFactory.getLogger(AccountDiscovery.class);

    @Autowired
    AccountDiscovery(DiscoveryServices discoveryServices, DiscoveryProperties discoveryProps,
                     IamClientFunctionRunner clientProvider) {
        this.discoveryServices = discoveryServices;
        this.clientProvider = clientProvider;
        this.discoveryProperties = discoveryProps;
    }

    /**
     * Executes a discovery run for this resource type. * @param resourceID is an optional (can be
     * null) filter to only run discovery for that resource ID * @return JSON of the discovered
     * resource(s), used only to confirm discovery took place and gathered expected data
     */
    public List<String> run(@Nullable String resourceID, String discoverySession) {
        List<String> jsons = new ArrayList<>();
        String region = Region.US_EAST_1.id();
        try {
            clientProvider.applyClient(region,
                    (client, credentialsProvider) -> {
                        final String accountId = getAccountId(credentialsProvider, region);
                        discoverAccounts(accountId, jsons, discoverySession, client);
                    });
        } catch (SdkServiceException e) {
            LOG.error(format("Failed for region %s", region), e);
        }

        return jsons;

    }

    private void discoverAccounts(String accountId, List<String> jsons, String discoverySession, IamClient client) {
        AccountResource data = new AccountResource();
        data.arn = format("arn:aws:organizations::%s", accountId);
        data.awsAccountId = accountId;
        data.awsRegion = null;
        data.resourceId = accountId;
        data.discoverySessionId = discoverySession;
        data.resourceName = client.listAccountAliases().accountAliases().stream().findFirst().orElse(null);
        data.resourceType = AccountResource.RESOURCE_TYPE;
        final Envelope envelope = new Envelope(
                this.discoveryProperties.getIntegration(), discoverySession,
                Instant.now().toString(), data);
        String json = discoveryServices.sendToKafka(envelope, data.resourceId);
        jsons.add(json);
    }
}
