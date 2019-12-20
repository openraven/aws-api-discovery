/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.fsx;

import io.openraven.data.interfaces.AWSResource;
import io.openraven.data.shared.PayloadUtils;
import java.time.Instant;
import software.amazon.awssdk.services.fsx.model.FileSystem;

public class FSxFileSystem extends AWSResource {

  public static final String RESOURCE_TYPE = "AWS::FSx::FileSystem";

  public FSxFileSystem() {
  }

  public FSxFileSystem(String regionId, FileSystem fileSystem) {
    this.awsRegion = regionId;
    this.arn = fileSystem.resourceARN();
    this.resourceId = fileSystem.fileSystemId();
    this.resourceName = fileSystem.fileSystemId();
    this.configuration = PayloadUtils.update(fileSystem);
    this.resourceType = RESOURCE_TYPE;
  }

}
