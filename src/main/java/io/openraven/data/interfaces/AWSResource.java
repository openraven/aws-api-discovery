/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.interfaces;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public abstract class AWSResource {

  public String arn;
  public String resourceName;
  public String resourceId;
  public String resourceType;
  public String awsRegion;
  public String awsAccountId;
  public String updatedIso;
  public String discoverySessionId;
  public String documentId;

  public JsonNode configuration;
  public JsonNode supplementaryConfiguration;
  public List<JsonNode> relationships;
  public JsonNode tags;
}
