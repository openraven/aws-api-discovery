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

@ConfigurationProperties(prefix = "openraven.app.v1.cloud-ingestion")
@RefreshScope
public class DiscoveryProperties {

	private String[] aws = new String[0];

	private String integration;

	public String[] getAws() {
		return aws;
	}

	public void setAws(final String[] aws) {
		this.aws = aws;
	}

	public String getIntegration() {
		return integration;
	}

	/**
	 * Determines the {@code .integration} field on the messages published into Kafka.
	 */
	public void setIntegration(final String integration) {
		this.integration = integration;
	}

}
