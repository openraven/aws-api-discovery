/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.ess;

import io.openraven.data.interfaces.AWSResource;
import io.openraven.data.shared.PayloadUtils;
import java.time.Instant;
import software.amazon.awssdk.services.elasticsearch.model.ElasticsearchDomainStatus;

public class EssDomain extends AWSResource {

  public static final String RESOURCE_TYPE = "AWS::Elasticsearch::Domain";

  public EssDomain() {
  }

  public EssDomain(String regionId, ElasticsearchDomainStatus domainStatus) {
    this.arn = domainStatus.arn();
    this.resourceId = domainStatus.domainId();
    this.resourceName = domainStatus.domainName();
    this.awsRegion = regionId;
    this.configuration = PayloadUtils.update(configuration, domainStatus);
    this.resourceType = RESOURCE_TYPE;
  }

}
