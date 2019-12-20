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

import java.util.ArrayList;
import java.util.List;

@Component
@RefreshScope
@ConfigurationProperties("openraven.accounts.crossaccount")
public class RoleArnConfig {

	public List<String> roleArns = new ArrayList<>();

	public List<String> getRoleArns() {
		return roleArns;
	}

	public void setRoleArns(List<String> roleArns) {
		this.roleArns = roleArns;
	}

}
