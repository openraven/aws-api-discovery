/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.efs;

import io.openraven.data.interfaces.AWSResource;
import io.openraven.data.shared.PayloadUtils;
import java.time.Instant;
import software.amazon.awssdk.services.efs.model.FileSystemDescription;

public class EfsFileSystem extends AWSResource {

  public static final String RESOURCE_TYPE = "AWS::EFS::FileSystem";

  public EfsFileSystem(){}

  public EfsFileSystem(String region, FileSystemDescription fileSystem) {
    resourceId = fileSystem.fileSystemId();
    awsAccountId = fileSystem.ownerId();
    awsRegion = region;
    arn = String
        .format("arn:aws:elasticfilesystem:%s:%s:file-system/%s", region, fileSystem.ownerId(),
            fileSystem.fileSystemId());
    resourceName = fileSystem.name();
    configuration = PayloadUtils.update(fileSystem);
    resourceType = RESOURCE_TYPE;
  }

}
