/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.s3;

import io.openraven.data.interfaces.AWSResource;
import io.openraven.data.shared.PayloadUtils;
import java.time.Instant;
import software.amazon.awssdk.services.s3.model.Bucket;

public class S3Bucket extends AWSResource {

  public static final String RESOURCE_TYPE = "AWS::S3::Bucket";

  public S3Bucket() {
  }

  public S3Bucket(Bucket bucket) {
    arn = "arn:aws:s3:::" + bucket.name();
    resourceName = bucket.name();
    resourceId = bucket.name();
    configuration = PayloadUtils.update(bucket);
    resourceType = RESOURCE_TYPE;
  }

}
