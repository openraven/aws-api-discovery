/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.interfaces;

@SuppressWarnings("unused")
public class Envelope {

  public String integration;

  public String discoverySession;

  public String ts;

  public AWSResource contents;

  public Envelope() {

  }

  public Envelope(final String integration, final String discoverySession, final String ts,
      final AWSResource contents) {
    this.integration = integration;
    this.discoverySession = discoverySession;
    this.ts = ts;
    this.contents = contents;
  }

  public String getIntegration() {
    return integration;
  }

  public void setIntegration(String integration) {
    this.integration = integration;
  }

  public String getDiscoverySession() {
    return discoverySession;
  }

  public void setDiscoverySession(String discoverySession) {
    this.discoverySession = discoverySession;
  }

  public String getTs() {
    return ts;
  }

  public void setTs(String ts) {
    this.ts = ts;
  }

  public AWSResource getContents() {
    return contents;
  }

  public void setContents(AWSResource contents) {
    this.contents = contents;
  }

}
