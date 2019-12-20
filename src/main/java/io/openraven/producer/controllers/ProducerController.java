/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.producer.controllers;

import io.openraven.data.interfaces.DiscoveryRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RefreshScope
@Profile("producer")
public class ProducerController {

	private final DiscoveryRegistry discoveryRegistry;

	@Autowired
	public ProducerController(DiscoveryRegistry discoveryRegistry) {
		this.discoveryRegistry = discoveryRegistry;
	}

	@GetMapping(path = "/discover", produces = MediaType.APPLICATION_JSON_VALUE)
	public String discover(@RequestParam(required = false) String id) {
		if (id == null) {
			return this.discoveryRegistry.discover();
		}
		else {
			return this.discoveryRegistry.discover(id);
		}
	}

}
