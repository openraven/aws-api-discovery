/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.rds;

import io.openraven.data.interfaces.AWSResource;
import io.openraven.data.shared.PayloadUtils;
import java.time.Instant;
import software.amazon.awssdk.services.rds.model.DBInstance;

public class RDSInstance extends AWSResource {

  public static final String RESOURCE_TYPE = "AWS::RDS::DBInstance";

  public RDSInstance() {
  }

  public RDSInstance(String regionId, DBInstance resource) {
    this.awsRegion = regionId;
    this.arn = resource.dbInstanceArn();
    this.resourceId = resource.dbInstanceArn();
    this.resourceName = resource.dbInstanceIdentifier();
    this.resourceType = RESOURCE_TYPE;
    this.configuration = PayloadUtils.update(resource);
  }

}
