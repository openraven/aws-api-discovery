/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.producer.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "openraven.app.v1.services.analytics")
@RefreshScope
public class AnalyticsProperties {

  private boolean enabled;

  private String key;

  private String clusterid;

  public boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public String getKey() {
    return key;
  }

  public void setKey(final String key) {
    this.key = key;
  }

  public String getClusterId() {
    return clusterid;
  }

  public void setClusterId(final String clusterid) {
    this.clusterid = clusterid;
  }

}
