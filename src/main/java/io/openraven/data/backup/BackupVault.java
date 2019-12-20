/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.backup;

import io.openraven.data.interfaces.AWSResource;
import io.openraven.data.shared.PayloadUtils;
import java.time.Instant;
import software.amazon.awssdk.services.backup.model.BackupVaultListMember;

public class BackupVault extends AWSResource {

  public static final String RESOURCE_TYPE = "AWS::Backup::BackupVault";

  public BackupVault() {
  }

  public BackupVault(BackupVaultListMember backupVaultListMember) {
    this.configuration = PayloadUtils.update(backupVaultListMember);
    this.resourceType = RESOURCE_TYPE;
    this.arn = backupVaultListMember.backupVaultArn();
    this.resourceName = backupVaultListMember.backupVaultName();
    this.resourceId = backupVaultListMember.backupVaultName();
  }
}
