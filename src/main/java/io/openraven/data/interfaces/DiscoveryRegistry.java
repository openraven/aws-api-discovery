/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.interfaces;

import static java.lang.String.format;

import com.google.common.collect.ImmutableMap;
import io.openraven.producer.properties.AnalyticsProperties;
import io.openraven.producer.services.DiscoveryServices;
import io.sentry.Sentry;
import io.sentry.event.BreadcrumbBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DiscoveryRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(DiscoveryRegistry.class);

  private final Collection<IDiscovery> discoveries;

  private final DiscoveryServices discoveryServices;

  private final String clusterId;

  @Autowired
  public DiscoveryRegistry(Collection<IDiscovery> discoveries, DiscoveryServices discoveryServices,
      AnalyticsProperties serviceProps) {

    this.discoveries = discoveries;
    this.discoveryServices = discoveryServices;
    this.clusterId = serviceProps.getClusterId();
  }

  public String discover() {
    return this.discover(null);
  }

  public String discover(String resourceID) {

    String discoverySession = UUID.randomUUID().toString();
    Sentry.getContext()
        .recordBreadcrumb(
            new BreadcrumbBuilder().setMessage("Discovery session " + discoverySession).build());

    List<String> outputs = new ArrayList<>();
    for (IDiscovery discovery : this.discoveries) {
      try {
        final String serviceName = discovery.getAnalyticsName();

        // send event on discovery session started to Segment for analytics
        var startPropertyMap = ImmutableMap
            .of("discovery-session", discoverySession, "clusterID", clusterId);
        discoveryServices.sendAnalyticsEvent(format("%s-start", serviceName), startPropertyMap);

        LOG.info("Starting {} in session {}", serviceName, discoverySession);

        Sentry.getContext()
            .recordBreadcrumb(new BreadcrumbBuilder().setMessage("In " + serviceName).build());

        // run discovery and capture output
        outputs.addAll(discovery.run(resourceID, discoverySession));

        // send event on how many resources were discovered to Segment for
        // analytics
        var completePropertyMap = ImmutableMap.of("discovery-session", discoverySession,
            "clusterID", clusterId, "num-resources", Integer.toString(outputs.size()));
        discoveryServices
            .sendAnalyticsEvent(format("%s-complete", serviceName), completePropertyMap);

        LOG.info("Ending {} and session {}", serviceName, discoverySession);

      } catch (Exception ex) {
        LOG.error(
            "Error calling through discovery registry - there should be nested exceptions detailing the issue",
            ex);
        Sentry.capture(ex);
      } finally {
        // ensure that everything is sent to Kafka before exiting
        discoveryServices.flushKafka();
      }
    }

    Sentry.getContext().clearBreadcrumbs();
    return String.join("\n\n", outputs);
  }

}
