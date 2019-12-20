/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.dynamodb;

import static org.springframework.util.StringUtils.isEmpty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openraven.data.interfaces.Envelope;
import io.openraven.data.interfaces.IDiscovery;
import io.openraven.data.shared.PayloadUtils;
import io.openraven.producer.properties.DiscoveryProperties;
import io.openraven.producer.services.DiscoveryServices;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeGlobalTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalTableDescription;
import software.amazon.awssdk.services.dynamodb.model.ListTagsOfResourceRequest;
import software.amazon.awssdk.services.dynamodb.model.ListTagsOfResourceResponse;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import software.amazon.awssdk.services.dynamodb.model.Tag;

@Profile("dynamoDb")
@Component
public class DynamoDbDiscovery implements IDiscovery {

  private static final String TABLE_DESCRIPTION_RESOURCE_TYPE = "AWS::DynamoDB::Table";
  private static final String GLOBAL_TABLE_DESCRIPTION_RESOURCE_TYPE = "AWS::DynamoDB::GlobalTable";

  private final DynamoDbClientFunctionRunner clientProvider;

  private final DiscoveryProperties discoveryProperties;

  private final DiscoveryServices discoveryServices;

  private static final Logger LOG = LoggerFactory.getLogger(DynamoDbDiscovery.class);

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  DynamoDbDiscovery(DiscoveryServices discoveryServices, DiscoveryProperties discoveryProps,
      DynamoDbClientFunctionRunner clientProvider) {
    this.clientProvider = clientProvider;
    this.discoveryProperties = discoveryProps;
    this.discoveryServices = discoveryServices;
  }

  @Override
  public List<String> run(String resourceID, String discoverySession) {
    List<String> jsons = new ArrayList<>();

    for (String region : discoveryProperties.getAws()) {
      try {
        clientProvider.applyClient(region,
            (client, credentialsProvider) -> doRun(resourceID, discoverySession,
                jsons, region, client, credentialsProvider));
      } catch (SdkServiceException e) {
        LOG.error(String.format("Failed for region %s", region), e);
      }
    }

    return jsons;
  }

  public static String getArn(SdkPojo inputObject) {
    String tableArn = "";
    if (inputObject instanceof GlobalTableDescription) {
      tableArn = getArn((GlobalTableDescription) inputObject);
    } else if (inputObject instanceof TableDescription) {
      tableArn = getArn((TableDescription) inputObject);
    }
    return tableArn;
  }

  private static String getArn(TableDescription table) {
    return table.tableArn();
  }

  private static String getArn(GlobalTableDescription globalTableDescription) {
    return globalTableDescription.globalTableArn();
  }

  private static String getTableName(SdkPojo inputObject) {
    if (inputObject instanceof TableDescription) {
      return ((TableDescription) inputObject).tableName();
    } else if (inputObject instanceof GlobalTableDescription) {
      return ((GlobalTableDescription) inputObject).globalTableName();
    } else {
      throw new IllegalStateException(String.format("inputObject is of unknown type: %s",
          inputObject.getClass().getName()));
    }
  }


  private void doRun(@Nullable String resourceID, String discoverySession, final List<String> jsons,
      String region,
      DynamoDbClient client, AwsCredentialsProvider credentialsProvider) {
    LOG.info("Starting discovery in region {}", region);
    final String accountId = getAccountId(credentialsProvider, region);
    Stream.concat(
        client.listTables().tableNames().stream()
            .map(tableName -> client
                .describeTable(DescribeTableRequest.builder().tableName(tableName).build()).table())
            .filter(table -> (resourceID == null || getArn(table).equalsIgnoreCase(resourceID)))
            .map(table -> Pair.with(table, new DynamoDbTable(table))),
        client.listGlobalTables().globalTables().stream()
            .map(globalTable -> client
                .describeGlobalTable(DescribeGlobalTableRequest.builder()
                    .globalTableName(globalTable.globalTableName()).build())
                .globalTableDescription())
            .filter(table -> (resourceID == null || getArn(table).equalsIgnoreCase(resourceID)))
            .map(
                globalTable ->
                    Pair.with(globalTable, new DynamoDbTable(globalTable)))).forEach(tuple ->
        {
          SdkPojo inputObject = tuple.getValue0();
          String tableArn = getArn(inputObject);
          if (isEmpty(tableArn)) {
            return;
          }
          DynamoDbTable data = tuple.getValue1();
          data.resourceType = getResourceType(inputObject);
          data.awsAccountId = accountId;
          data.awsRegion = region;
          data.discoverySessionId = discoverySession;

          discover(client, tuple.getValue0(), data);

          final Envelope envelope = new Envelope(discoveryProperties.getIntegration(),
              discoverySession, Instant.now().toString(), data);
          String json = discoveryServices.sendToKafka(envelope, tableArn);
          jsons.add(json);
        }
    );

    LOG.info("Finished discovery in region {}", region);
  }

  private static String getResourceType(SdkPojo inputObject) {
    String tableArn = "";
    if (inputObject instanceof GlobalTableDescription) {
      tableArn = getResourceType((GlobalTableDescription) inputObject);
    } else if (inputObject instanceof TableDescription) {
      tableArn = getResourceType((TableDescription) inputObject);
    }
    return tableArn;
  }

  private static String getResourceType(TableDescription table) {
    return TABLE_DESCRIPTION_RESOURCE_TYPE;
  }

  private static String getResourceType(GlobalTableDescription globalTableDescription) {
    return GLOBAL_TABLE_DESCRIPTION_RESOURCE_TYPE;
  }

  public void discover(DynamoDbClient client, SdkPojo input, DynamoDbTable data) {
    ListTagsOfResourceResponse listTagsOfResourceResponse = client.listTagsOfResource(
        ListTagsOfResourceRequest.builder().resourceArn(DynamoDbDiscovery.getArn(input)).build());

    getTags(data, listTagsOfResourceResponse.tags());

    var describeContinuousBackupsResponse = client
        .describeContinuousBackups(builder -> builder.tableName(getTableName(input)));

    data.supplementaryConfiguration = PayloadUtils
        .update(data.supplementaryConfiguration, describeContinuousBackupsResponse);
  }

  private void getTags(DynamoDbTable table, List<Tag> tags) {
    JsonNode tagsNode = objectMapper
        .convertValue(tags.stream().collect(
            Collectors.toMap(Tag::key, Tag::value)), JsonNode.class);

    table.tags = PayloadUtils.update(table.tags, tagsNode);

  }
}
