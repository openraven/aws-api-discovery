/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.producer.properties;

import com.google.common.base.Charsets;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.zookeeper.config.ZookeeperConfigProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("zookeeper-initializer")
public class ZookeeperInitializer implements InitializingBean {

	private static final Logger LOG = LoggerFactory.getLogger(ZookeeperInitializer.class);

	public static final String ROOT_DEFAULT_CONTEXT_PATH_FORMAT = "/%s/%s/%s";

	private final SchedulingProperties schedulingProperties;

	private final ZookeeperConfigProperties zookeeperConfigProperties;

	private final CuratorFramework curatorFramework;

	public ZookeeperInitializer(SchedulingProperties schedulingProperties,
			ZookeeperConfigProperties zookeeperConfigProperties, CuratorFramework curatorFramework) {
		this.schedulingProperties = schedulingProperties;
		this.zookeeperConfigProperties = zookeeperConfigProperties;
		this.curatorFramework = curatorFramework;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		String context = createDefaultContextForPath(this.schedulingProperties.getConfigPath());
		String[] split = context.split("/");
		String configName = split[split.length - 1];
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < split.length - 1; i++) {
			String part = split[i];
			if (part.isEmpty()) {
				continue;
			}
			sb.append("/");
			sb.append(part);
			Stat stat = curatorFramework.checkExists().forPath(sb.toString());
			LOG.debug("Stat for configPath: {}", stat);
			if (stat == null) {
				curatorFramework.create().forPath(sb.toString(), "".getBytes(Charsets.UTF_8));
			}
		}
		sb.append("/").append(configName);

		Stat stat = curatorFramework.checkExists().forPath(sb.toString());
		if (stat == null) {
			curatorFramework.create().forPath(sb.toString(), schedulingProperties.getCron().getBytes(Charsets.UTF_8));
		}
	}

	private String createDefaultContextForPath(String path) {
		String defaultContext = zookeeperConfigProperties.getDefaultContext();
		String root = zookeeperConfigProperties.getRoot();

		return String.format(ROOT_DEFAULT_CONTEXT_PATH_FORMAT, root, defaultContext, path);
	}

}
