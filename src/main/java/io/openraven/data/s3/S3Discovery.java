/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.s3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openraven.data.interfaces.Envelope;
import io.openraven.data.interfaces.IDiscovery;
import io.openraven.data.shared.PayloadUtils;
import io.openraven.data.shared.Utils;
import io.openraven.producer.properties.DiscoveryProperties;
import io.openraven.producer.services.DiscoveryServices;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.GetBucketAclRequest;
import software.amazon.awssdk.services.s3.model.GetBucketAclResponse;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionRequest;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionResponse;
import software.amazon.awssdk.services.s3.model.GetBucketLocationRequest;
import software.amazon.awssdk.services.s3.model.GetBucketLoggingRequest;
import software.amazon.awssdk.services.s3.model.GetBucketLoggingResponse;
import software.amazon.awssdk.services.s3.model.GetBucketMetricsConfigurationRequest;
import software.amazon.awssdk.services.s3.model.GetBucketMetricsConfigurationResponse;
import software.amazon.awssdk.services.s3.model.GetBucketReplicationRequest;
import software.amazon.awssdk.services.s3.model.GetBucketReplicationResponse;
import software.amazon.awssdk.services.s3.model.GetBucketTaggingRequest;
import software.amazon.awssdk.services.s3.model.GetBucketTaggingResponse;
import software.amazon.awssdk.services.s3.model.GetBucketWebsiteRequest;
import software.amazon.awssdk.services.s3.model.GetBucketWebsiteResponse;
import software.amazon.awssdk.services.s3.model.GetObjectLockConfigurationRequest;
import software.amazon.awssdk.services.s3.model.GetObjectLockConfigurationResponse;
import software.amazon.awssdk.services.s3.model.GetPublicAccessBlockRequest;
import software.amazon.awssdk.services.s3.model.GetPublicAccessBlockResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.Tag;

@Profile("S3")
@Component
public class S3Discovery implements IDiscovery {

  private final S3ClientFunctionRunner clientProvider;

  private final DiscoveryServices discoveryServices;

  private final DiscoveryProperties discoveryProperties;

  private static final Logger LOG = LoggerFactory.getLogger(S3Discovery.class);

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  S3Discovery(DiscoveryServices discoveryServices, DiscoveryProperties discoveryProps,
      S3ClientFunctionRunner clientProvider) {
    this.discoveryServices = discoveryServices;
    this.clientProvider = clientProvider;
    this.discoveryProperties = discoveryProps;
  }

  private boolean isInRegion(String region, S3Client client, Bucket resource) {
    try {
      String bucketRegion = client
          .getBucketLocation(GetBucketLocationRequest.builder().bucket(resource.name()).build())
          .locationConstraintAsString();
      return bucketRegion.equalsIgnoreCase(region);

    } catch (S3Exception ex) {
      if (ex.statusCode() >= 300 && ex.statusCode() < 500) {
        // if exception, then the client is for a different region than
        // the resource was in.
        LOG.debug("Cross-region request for resource={}, {}", resource.name(), ex.getMessage());
        return false;
      } else {
        throw ex;
      }
    }
  }

  /**
   * Executes a discovery run for this resource type.
   *
   * @param resourceID is an optional (can be null) filter to only run discovery for that resource
   *                   ID
   * @return JSON of the discovered resource(s), used only to confirm discovery took place and
   * gathered expected data
   */
  public List<String> run(@Nullable String resourceID, String discoverySession) {
    List<String> jsons = new ArrayList<>();

    for (String region : this.discoveryProperties.getAws()) {
      try {
        clientProvider.applyClient(region, (s3Client, credentialsProvider) -> doRun(resourceID,
            discoverySession, jsons, region, s3Client, credentialsProvider));
      } catch (SdkServiceException e) {
        LOG.error(String.format("Failed for region %s", region), e);
      }
    }

    return jsons;

  }

  private void doRun(@Nullable String resourceID, String discoverySession, List<String> jsons,
      String region,
      S3Client client, AwsCredentialsProvider credentialsProvider) {
    LOG.info("Starting discovery in region {}", region);
    String accountId = getAccountId(credentialsProvider, region);
    client.listBuckets().buckets().stream().filter(
        b -> (isInRegion(region, client, b) && (resourceID == null || b.name()
            .equalsIgnoreCase(resourceID))))
        .forEach(b -> {
          S3Bucket data = new S3Bucket(b);
          data.awsRegion = region;
          data.awsAccountId = accountId;
          data.discoverySessionId = discoverySession;

          discover(client, b, data);
          discoverMe(client, b, data, credentialsProvider);

          final Envelope envelope = new Envelope(
              this.discoveryProperties.getIntegration(),
              discoverySession, Instant.now().toString(), data);
          String json = discoveryServices.sendToKafka(envelope, data.resourceId);
          jsons.add(json);
        });

    LOG.info("Finished discovery in region {}", region);
  }

  private void discoverMe(S3Client client, Bucket resource, S3Bucket data,
      AwsCredentialsProvider awsCredentialsProvider) {
    discoveryACLS(client, resource, data);
    discoverEncryption(client, resource, data);
    discoverHosting(client, resource, data);
    discoverLogging(client, resource, data);
    discoverMetrics(client, resource, data);
    discoverPublicAccess(client, resource, data);
    discoverSize(resource, data, awsCredentialsProvider);
  }

  private void discoveryACLS(S3Client client, Bucket resource, S3Bucket data) {
    LOG.info("Getting ACLs for {}", resource.name());

    try {
      GetBucketAclResponse bucketAcl = client
          .getBucketAcl(GetBucketAclRequest.builder().bucket(resource.name()).build());
      data.supplementaryConfiguration = PayloadUtils
          .update(data.supplementaryConfiguration, Map.of("BucketACLConfiguration", bucketAcl));
    } catch (SdkException se) {
      LOG.warn("{} bucket is missing ACLS, with error {}", resource.name(), se.getMessage());
    }
  }

  private void discoverEncryption(S3Client client, Bucket resource, S3Bucket data) {
    LOG.info("Getting encryption keys for {}", resource.name());

    try {
      GetBucketEncryptionResponse bucketEncryption = client
          .getBucketEncryption(
              GetBucketEncryptionRequest.builder().bucket(resource.name()).build());

      data.supplementaryConfiguration = PayloadUtils
          .update(data.supplementaryConfiguration,
              Map.of("ServerSideEncryptionConfiguration", bucketEncryption));
    } catch (SdkException se) {
      LOG.warn("{} bucket is missing Encryption, with error {}", resource.name(), se.getMessage());
    }
  }

  private void discoverHosting(S3Client client, Bucket resource, S3Bucket data) {
    LOG.info("Getting hosting settings for {}", resource.name());

    try {
      GetBucketWebsiteResponse bucketWebsite = client
          .getBucketWebsite(GetBucketWebsiteRequest.builder().bucket(resource.name()).build());

      data.supplementaryConfiguration = PayloadUtils
          .update(data.supplementaryConfiguration,
              Map.of("BucketWebsiteConfiguration", bucketWebsite));
    } catch (SdkException se) {
      LOG.warn("{} bucket is missing Hosting, with error {}", resource.name(), se.getMessage());
    }
  }

  private void discoverLogging(S3Client client, Bucket resource, S3Bucket data) {
    LOG.info("Getting logging settings for {}", resource.name());

    try {
      GetBucketLoggingResponse response = client
          .getBucketLogging(GetBucketLoggingRequest.builder().bucket(resource.name()).build());

      data.supplementaryConfiguration = PayloadUtils
          .update(data.supplementaryConfiguration, Map.of("BucketLoggingConfiguration", response));
    } catch (SdkException se) {
      LOG.warn("{} bucket is missing Logging, with error {}", resource.name(), se.getMessage());
    }
  }

  private void discoverMetrics(S3Client client, Bucket resource, S3Bucket data) {
    LOG.info("Getting metrics settings for {}", resource.name());

    try {
      GetBucketMetricsConfigurationResponse response = client.getBucketMetricsConfiguration(
          GetBucketMetricsConfigurationRequest.builder().bucket(resource.name()).build());
      data.supplementaryConfiguration = PayloadUtils
          .update(data.supplementaryConfiguration, response);
    } catch (SdkException se) {
      LOG.warn("{} bucket is missing Metricss, with error {}", resource.name(), se.getMessage());
    }
  }

  private void discoverPublicAccess(S3Client client, Bucket resource, S3Bucket data) {

    LOG.info("Getting access settings for {}", resource.name());
    try {
      GetPublicAccessBlockRequest req = GetPublicAccessBlockRequest.builder()
          .bucket(resource.name())
          .build();
      GetPublicAccessBlockResponse resp = client.getPublicAccessBlock(req);

      data.supplementaryConfiguration = PayloadUtils
          .update(data.supplementaryConfiguration,
              Map.of("PublicAccessBlockConfiguration", resp.publicAccessBlockConfiguration()));
    } catch (SdkException se) {
      LOG.warn("{} bucket is missing PublicAccess, with error {}", resource.name(),
          se.getMessage());
    }
  }

  private void discoverSize(Bucket resource, S3Bucket data,
      AwsCredentialsProvider awsCredentialsProvider) {
    LOG.info("Getting size for bucket {}", resource.name());

    List<Dimension> dimensions = new ArrayList<>();
    dimensions.add(Dimension.builder().name("BucketName").value(resource.name()).build());
    dimensions.add(Dimension.builder().name("StorageType").value("StandardStorage").build());
    Pair<Long, GetMetricStatisticsResponse> bucketSizeBytes1 = Utils
        .getCloudwatchMetricMinimum(data.awsRegion, "AWS/S3", "BucketSizeBytes",
            dimensions, awsCredentialsProvider);

    List<Dimension> dimensions2 = new ArrayList<>();
    dimensions2.add(Dimension.builder().name("BucketName").value(resource.name()).build());
    dimensions2.add(Dimension.builder().name("StorageType").value("AllStorageTypes").build());
    Pair<Long, GetMetricStatisticsResponse> numberOfObjects = Utils
        .getCloudwatchMetricMinimum(data.awsRegion, "AWS/S3", "NumberOfObjects",
            dimensions2, awsCredentialsProvider);

    data.supplementaryConfiguration = PayloadUtils
        .update(data.supplementaryConfiguration, Map.of("size",
            Map.of("BucketSizeBytes", bucketSizeBytes1.getValue0(),
                "NumberOfObjects", numberOfObjects.getValue0())));
  }

  public void discover(S3Client client, Bucket resource, S3Bucket data) {

    String bucketName = resource.name();

    try {
      GetObjectLockConfigurationResponse objectLockConfigurationResponse = client
          .getObjectLockConfiguration(
              GetObjectLockConfigurationRequest
                  .builder()
                  .bucket(bucketName)
                  .build()
          );

      data.supplementaryConfiguration = PayloadUtils.update(data.supplementaryConfiguration,
          Map.of("BucketObjectLockConfiguration",
              objectLockConfigurationResponse.objectLockConfiguration()));
    } catch (SdkServiceException sdkException) {
      if (sdkException.statusCode() != 404) {
        throw sdkException;
      }
    }

    LOG.info("Getting tags for {}", bucketName);
    try {
      GetBucketTaggingResponse getBucketTaggingResponse = client
          .getBucketTagging(GetBucketTaggingRequest.builder().bucket(bucketName).build());

      data.supplementaryConfiguration = PayloadUtils
          .update(data.supplementaryConfiguration, getBucketTaggingResponse);

      JsonNode tagsNode = objectMapper
          .convertValue(getBucketTaggingResponse.tagSet().stream().collect(
              Collectors.toMap(Tag::key, Tag::value)), JsonNode.class);

      data.tags = PayloadUtils.update(data.tags, tagsNode);
    } catch (SdkServiceException sdkException) {
      if (sdkException.statusCode() != 404) {
        throw sdkException;
      }
    }

    try {
      GetBucketReplicationResponse bucketReplication = client.getBucketReplication(
          GetBucketReplicationRequest.builder().bucket(resource.name()).build());

      data.supplementaryConfiguration = PayloadUtils.update(data.supplementaryConfiguration,
          Map.of("BucketReplicationConfiguration", bucketReplication.replicationConfiguration()));
    } catch (SdkServiceException sdkException) {
      if (sdkException.statusCode() != 404) {
        throw sdkException;
      }
    }
  }

}
