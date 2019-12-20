/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.ec2;

import io.openraven.data.interfaces.AWSResource;
import io.openraven.data.shared.PayloadUtils;
import java.time.Instant;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.Reservation;

public class EC2Instance extends AWSResource {

  public static final String RESOURCE_TYPE = "AWS::EC2::Instance";

  public EC2Instance() {
  }

  public EC2Instance(Instance instance, Reservation r, String region) {
    this.resourceName = instance.instanceId();
    this.resourceId = instance.instanceId();
    this.awsRegion = region;
    this.resourceType = RESOURCE_TYPE;
    this.arn = String
        .format("arn:aws:ec2:%s:%s:instance/%s", region, r.ownerId(), instance.instanceId());
    this.configuration = PayloadUtils.update(instance, r);
  }

}
