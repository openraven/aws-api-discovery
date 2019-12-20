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

@ConfigurationProperties("openraven.app.v1.scheduling")
@RefreshScope
public class SchedulingProperties {

	String cron;

	String configPath;

	boolean runOnStart;

	public String getCron() {
		return cron;
	}

	/**
	 * The spring scheduling <a href=
	 * "https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/scheduling/support/CronSequenceGenerator.html">a
	 * cron formatted string</a>
	 * @param cron The cron schedule. Note: No matter this value, discovery always occurs
	 * on first start.
	 */
	public void setCron(String cron) {
		this.cron = cron;
	}

	public String getConfigPath() {
		return configPath;
	}

	/**
	 * The sub path, not including the root/defaultContext for where the cron format
	 * string is configured in zookeeper. Should never end in a "/". The final part of
	 * this name should be the name of the cron format string supported in this config,
	 * currently "cron". If you change that name, you should change this value.
	 * @param configPath a path value for zookeeper, eg
	 * "openraven/app/v1/scheduling/cron".
	 */
	public void setConfigPath(String configPath) {
		this.configPath = configPath;
	}

	public boolean getRunOnStart() {
		return runOnStart;
	}

	/**
	 * if the service should auto run after the application has started. Should be set to
	 * 'true' in production, but useful to control while in development.
	 */
	public void setRunOnStart(boolean runOnStart) {
		this.runOnStart = runOnStart;
	}

}
