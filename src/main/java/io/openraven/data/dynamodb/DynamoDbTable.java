/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.dynamodb;

import io.openraven.data.interfaces.AWSResource;
import io.openraven.data.shared.PayloadUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.services.dynamodb.model.GlobalTableDescription;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import software.amazon.awssdk.utils.builder.ToCopyableBuilder;

@Profile("dynamoDb")
@Component
public class DynamoDbTable extends AWSResource {

  public DynamoDbTable() {
  }

  public DynamoDbTable(SdkPojo table) {
    //noinspection rawtypes
    this.configuration = PayloadUtils.update((ToCopyableBuilder) table);
  }

  public DynamoDbTable(GlobalTableDescription globalTableDescription) {
    this((SdkPojo) globalTableDescription);
    resourceName = globalTableDescription.globalTableName();
    resourceId = globalTableDescription.globalTableName();
    arn = globalTableDescription.globalTableArn();
  }

  public DynamoDbTable(TableDescription tableDescription) {
    this((SdkPojo) tableDescription);
    resourceName = tableDescription.tableName();
    resourceId = tableDescription.tableId();
    arn = tableDescription.tableArn();
  }
}
