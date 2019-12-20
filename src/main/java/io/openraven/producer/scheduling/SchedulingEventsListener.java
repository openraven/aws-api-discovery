/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.producer.scheduling;

import io.openraven.producer.properties.AnalyticsProperties;
import io.openraven.producer.properties.SchedulingProperties;
import io.sentry.Sentry;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class SchedulingEventsListener {

    private final OpenravenSchedulerConfigurer openravenSchedulerImpl;

    private final SchedulingProperties schedulingProperties;

    private final ScheduledDiscovery scheduledDiscovery;
    private final ExecutorService executorService;

    public SchedulingEventsListener(OpenravenSchedulerConfigurer openravenSchedulerImpl,
                                    SchedulingProperties schedulingProperties, ScheduledDiscovery scheduledDiscovery,
                                    AnalyticsProperties analyticsProperties) {
        this.openravenSchedulerImpl = openravenSchedulerImpl;
        this.schedulingProperties = schedulingProperties;
        this.scheduledDiscovery = scheduledDiscovery;
        if(analyticsProperties.getEnabled()) {
            this.executorService = Executors.newSingleThreadExecutor(runnable -> {
                Thread result = new Thread(runnable);
                result.setUncaughtExceptionHandler((t, e) -> Sentry.capture(e));
                return result;
            });
        }
        else {
            this.executorService = Executors.newSingleThreadExecutor();
        }

    }

    @EventListener({ApplicationReadyEvent.class})
    public void refresh() {
        this.openravenSchedulerImpl.schedule();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void discoverOnStartMaybe() {
        if (schedulingProperties.getRunOnStart()) {
            executorService.execute(scheduledDiscovery::discover);
        }
    }

}
