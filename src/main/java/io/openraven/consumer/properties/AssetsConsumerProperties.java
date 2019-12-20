/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.consumer.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "openraven.app.v1.kafka")
@RefreshScope
@Component
public class AssetsConsumerProperties {

	/**
	 * Use this environment variable to <b>default</b> the gremlin password, but the
	 * setters always supersede that <b>default</b>.
	 */
	private Consumer consumer = new Consumer();

	private DeadLetter deadLetter = new DeadLetter();

	public Consumer getConsumer() {
		return consumer;
	}

	public void setConsumer(final Consumer consumer) {
		this.consumer = consumer;
	}

	public DeadLetter getDeadLetter() {
		return deadLetter;
	}

	public void setDeadLetter(final DeadLetter deadLetter) {
		this.deadLetter = deadLetter;
	}

	/**
	 * Values related to how one handles dead letter messages.
	 */
	public static class DeadLetter {

		private String topic;

		public String getTopic() {
			return topic;
		}

		public void setTopic(final String topic) {
			this.topic = topic;
		}

	}

	/**
	 * OpenRaven specific configuration of Kafka consumers.
	 */
	public static class Consumer {

		private String group;

		private String topic;

		public String getGroup() {
			return group;
		}

		public void setGroup(final String group) {
			this.group = group;
		}

		public String getTopic() {
			return topic;
		}

		public void setTopic(final String topic) {
			this.topic = topic;
		}

	}

}
