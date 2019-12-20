/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.ec2;

import static java.lang.String.format;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openraven.data.interfaces.AWSResource;
import io.openraven.data.interfaces.Envelope;
import io.openraven.data.interfaces.IDiscovery;
import io.openraven.data.shared.PayloadUtils;
import io.openraven.producer.properties.DiscoveryProperties;
import io.openraven.producer.services.DiscoveryServices;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.RegionMetadata;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeVpcsResponse;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.Tag;

@Profile("EC2")
@Component
public class Ec2Discovery implements IDiscovery {

  private final Ec2ClientFunctionRunner clientProvider;

  private final DiscoveryServices discoveryServices;

  private final DiscoveryProperties discoveryProperties;

  private static final Logger LOG = LoggerFactory.getLogger(Ec2Discovery.class);

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  Ec2Discovery(DiscoveryServices discoveryServices, DiscoveryProperties discoveryProps,
      Ec2ClientFunctionRunner clientProvider) {
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
    final String singleDiscoveryRegion = Region.US_EAST_1.id();
    clientProvider.applyClient(singleDiscoveryRegion,
        (client, credentialsProvider) -> {
          discoverRegions(getAccountId(credentialsProvider, singleDiscoveryRegion), jsons,
              discoverySession,
              singleDiscoveryRegion);
        });
    for (String region : this.discoveryProperties.getAws()) {
      try {
        clientProvider.applyClient(region,
            (client, credentialsProvider) -> {
              doRun(resourceID, discoverySession, jsons, region, client, credentialsProvider);
            });
      } catch (SdkServiceException e) {
        LOG.error(format("Failed for region %s", region), e);
      }
    }

    return jsons;

  }

  private void doRun(@Nullable String resourceID, String discoverySession, List<String> jsons,
      String region,
      Ec2Client client, AwsCredentialsProvider credentialsProvider) {
    LOG.info("Starting discovery in region {}", region);
    String accountId = getAccountId(credentialsProvider, region);
    discoverEc2Instances(accountId, resourceID, discoverySession, jsons, region, client);
    discoverVpcs(accountId, resourceID, discoverySession, jsons, region, client);
    LOG.info("Finished discovery in region {}", region);
  }

  private void discoverEc2Instances(String accountId, @Nullable String resourceID,
      String discoverySession, List<String> jsons, String region, Ec2Client client) {
    LOG.info("Discovering EC2 Instances in region {}", region);
    client.describeInstances().reservations()
        .forEach(
            r -> r.instances().stream()
                .filter(i -> (resourceID == null || String
                    .format("arn:aws:ec2:%s:%s:instance/%s", region, r.ownerId(), i.instanceId())
                    .equalsIgnoreCase(resourceID)))
                .forEach(i -> {
                  EC2Instance data = new EC2Instance(i, r, region);
                  data.awsAccountId = accountId;
                  data.discoverySessionId = discoverySession;

                  final Envelope envelope = new Envelope(
                      this.discoveryProperties.getIntegration(), discoverySession,
                      Instant.now().toString(), data);
                  getTags(data, i);
                  data = massage(data, i);

                  String json = discoveryServices.sendToKafka(envelope, data.resourceId);
                  jsons.add(json);
                }));
    LOG.info("Finished discovering EC2 Instances in region {}", region);
  }

  private void getTags(EC2Instance ec2Instance, Instance instance) {
    JsonNode tagsNode = objectMapper
        .convertValue(instance.tags().stream().collect(
            Collectors.toMap(Tag::key, Tag::value)), JsonNode.class);

    ec2Instance.tags = PayloadUtils.update(ec2Instance.tags, tagsNode);

  }

  private void discoverVpcs(String accountId, @Nullable String resourceID, String discoverySession,
      List<String> jsons, String region, Ec2Client client) {
    LOG.info("Discovering Vpcs in region {}", region);
    final DescribeVpcsResponse describeVpcsResponse = client.describeVpcs();
    describeVpcsResponse.vpcs().stream().filter(
        vpc -> (resourceID == null || format("arn:aws:ec2:%s:%s:vpc/%s", region, accountId,
            vpc.vpcId())
            .equalsIgnoreCase(resourceID))).forEach(
        vpc -> {
          VpcResource data = new VpcResource();
          data.arn = format("arn:aws:ec2:%s:%s:vpc/%s", region, accountId, vpc.vpcId());
          data.awsAccountId = accountId;
          data.awsRegion = region;
          data.resourceId = vpc.vpcId();
          data.resourceName = vpc.vpcId();
          data.resourceType = VpcResource.RESOURCE_TYPE;
          data.configuration = PayloadUtils.update(vpc);
          data.tags = PayloadUtils.add(vpc.tags());
          final Envelope envelope = new Envelope(
              this.discoveryProperties.getIntegration(), discoverySession,
              Instant.now().toString(), data);
          String json = discoveryServices.sendToKafka(envelope, data.resourceId);
          jsons.add(json);
        }
    );
    LOG.info("Finished discovering Vpcs in region {}", region);
  }

  private void discoverRegions(String accountId, List<String> jsons, String discoverySession,
      String regionName) {
    LOG.info("Discovering Regions in region {}", regionName);
    LOG.info("Finished discovering Regions in region {}", regionName);
    List<Region> copy = new ArrayList<>(Region.regions());
    copy.removeAll(Arrays.asList(Region.AWS_US_GOV_GLOBAL, Region.AWS_CN_GLOBAL));

    copy.forEach(region -> {
      RegionResource data = new RegionResource();
      data.arn = format("arn:aws::%s:%s", region.id(), accountId);
      data.awsAccountId = accountId;
      data.awsRegion = region.id();
      data.resourceId = region.id();
      final RegionMetadata metadata = region.metadata();
      if (metadata != null) {
        data.resourceName = metadata.description();
      }
      data.resourceType = RegionResource.RESOURCE_TYPE;
      data.configuration = null;
      final Envelope envelope = new Envelope(
          this.discoveryProperties.getIntegration(), discoverySession,
          Instant.now().toString(), data);
      String json = discoveryServices.sendToKafka(envelope, data.resourceId);
      jsons.add(json);
    });
  }

  public static class VpcResource extends AWSResource {

    public static final String RESOURCE_TYPE = "AWS::EC2::VPC";
  }

  public static class RegionResource extends AWSResource {

    public static final String RESOURCE_TYPE = "AWS::Region";
  }

  public EC2Instance massage(EC2Instance ec2Instance, Instance instance) {
    try {
      var instanceForUpdate = objectMapper.readerForUpdating(ec2Instance.configuration);

      ec2Instance.configuration = instanceForUpdate.readValue(objectMapper.convertValue(
          Map.of("instanceType", instance.instanceTypeAsString()), JsonNode.class));

      if(!StringUtils.isEmpty(instance.publicIpAddress())) {
        ec2Instance.configuration = instanceForUpdate.readValue(objectMapper.convertValue(
            Map.of("publicIp", instance.publicIpAddress()), JsonNode.class));
      }

    } catch (IOException e) {
      LOG.warn("Error updating {} data object: {}", ec2Instance.resourceId, e.getMessage());
    }

    return ec2Instance;
  }

}
