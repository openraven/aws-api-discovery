/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.producer.scheduling;

import io.openraven.producer.properties.SchedulingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronSequenceGenerator;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.TaskUtils;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
@RefreshScope
@Scope("singleton")
public class OpenravenSchedulerConfigurer implements SchedulingConfigurer {

	private final SchedulingProperties schedulingProperties;

	private final ScheduledDiscovery scheduledDiscovery;

	private ScheduledTaskRegistrar scheduledTaskRegistrar;

	private static final Logger LOG = LoggerFactory.getLogger(OpenravenSchedulerConfigurer.class);

	private Date lastScheduledRun;

	public OpenravenSchedulerConfigurer(SchedulingProperties schedulingProperties,
			ScheduledDiscovery scheduledDiscovery) {
		this.schedulingProperties = schedulingProperties;
		this.scheduledDiscovery = scheduledDiscovery;
	}

	public void schedule() {
		String cronExpression = this.schedulingProperties.getCron();
		final CronTask task = new CronTask(scheduledDiscovery::discover, new CronTrigger(cronExpression) {
			@Override
			public Date nextExecutionTime(TriggerContext triggerContext) {
				Date date = triggerContext.lastCompletionTime();
				if (date != null) {
					Date scheduled = triggerContext.lastScheduledExecutionTime();
					if (scheduled != null && date.before(scheduled)) {
						// Previous task apparently executed too early...
						// Let's simply use the last calculated execution time then,
						// in order to prevent accidental re-fires in the same second.
						date = scheduled;
					}
				}
				else {
					date = new Date();
				}
				final String cron = schedulingProperties.getCron();
				LOG.debug("nextExecutionTime with cron expression {}", cron);
				final Date next = new CronSequenceGenerator(cron).next(date);
				LOG.debug("next scheduled execution date is {}", next);
				return next;
			}
		});
		scheduledTaskRegistrar.scheduleCronTask(task);
		lastScheduledRun = new CronSequenceGenerator(cronExpression).next(new Date());
		LOG.debug("Scheduled task at {}", lastScheduledRun);
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
		scheduledTaskRegistrar = taskRegistrar;
	}

}