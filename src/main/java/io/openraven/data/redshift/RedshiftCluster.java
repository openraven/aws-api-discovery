/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.redshift;

import io.openraven.data.interfaces.AWSResource;
import io.openraven.data.shared.PayloadUtils;
import java.time.Instant;
import software.amazon.awssdk.services.redshift.model.Cluster;

public class RedshiftCluster extends AWSResource {

  public static final String RESOURCE_TYPE = "AWS::Redshift::Cluster";
  public String resourceCreationTime;

    public RedshiftCluster() {
  }

  public RedshiftCluster(String regionId, Cluster cluster, String accountId) {
    this.awsRegion = regionId;
    this.resourceType = RESOURCE_TYPE;
    this.resourceId = cluster.clusterIdentifier();
    this.arn = String.format("arn:aws:redshift:%s:%s:cluster:%s", regionId, accountId,
        cluster.clusterIdentifier());
    this.resourceName = cluster.dbName();
    this.configuration = PayloadUtils.update(cluster);
    this.awsAccountId = accountId;
    this.resourceCreationTime = cluster.clusterCreateTime().toString();
  }

}
