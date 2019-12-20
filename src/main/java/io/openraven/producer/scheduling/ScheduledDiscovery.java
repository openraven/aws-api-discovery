/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.producer.scheduling;

import io.openraven.data.interfaces.DiscoveryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ScheduledDiscovery {

	private final DiscoveryRegistry discoveryRegistry;

	private static final Logger LOG = LoggerFactory.getLogger(ScheduledDiscovery.class);

	@Autowired
	public ScheduledDiscovery(DiscoveryRegistry discoveryRegistry) {
		this.discoveryRegistry = discoveryRegistry;
	}

	public void discover() {
		try {
			LOG.debug("Running discovery from scheduled service");
			this.discoveryRegistry.discover(null);
			LOG.debug("Discovery complete");
		} catch (Exception e) {
			LOG.debug("Failed to complete discovery during scheduled execution", e);
		}
	}

}
