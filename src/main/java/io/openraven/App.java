/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven;

import static org.springframework.util.StringUtils.isEmpty;

import io.openraven.consumer.properties.AssetsConsumerProperties;
import io.openraven.consumer.properties.ElasticSearchServiceProperties;
import io.openraven.producer.properties.AnalyticsProperties;
import io.openraven.producer.properties.DiscoveryProperties;
import io.openraven.producer.properties.RoleArnConfig;
import io.openraven.producer.properties.SchedulingProperties;
import io.sentry.Sentry;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.HandlerExceptionResolver;

@SpringBootApplication
public class App {

  public static void main(String[] args) {
    Sentry.init();
    new SpringApplicationBuilder(App.class).bannerMode(Banner.Mode.OFF)
        .web(WebApplicationType.SERVLET).run(args);
  }

  @Configuration
  @EnableConfigurationProperties({DiscoveryProperties.class, SchedulingProperties.class,
      AssetsConsumerProperties.class, RoleArnConfig.class, ElasticSearchServiceProperties.class,
      AnalyticsProperties.class})

  @EnableKafka
  @EnableScheduling
  public static class Config {

    @Bean
    @Profile("producer")
    @SuppressWarnings("unchecked")
    public ProducerFactory<Long, String> producerFactory(KafkaProperties properties)
        throws Exception {
      final KafkaProperties.Producer kppProps = properties.getProducer();
      final Class<Serializer<Long>> keySerClass = (Class<Serializer<Long>>) kppProps
          .getKeySerializer();
      final Class<Serializer<String>> valueSerClass = (Class<Serializer<String>>) kppProps
          .getValueSerializer();
      final Serializer<Long> keySer = keySerClass.getDeclaredConstructor().newInstance();
      final Serializer<String> valueSer = valueSerClass.getDeclaredConstructor().newInstance();
      final DefaultKafkaProducerFactory<Long, String> result = new DefaultKafkaProducerFactory<>(
          properties.buildProducerProperties());
      result.setKeySerializer(keySer);
      result.setValueSerializer(valueSer);
      return result;
    }

    @Bean
    public KafkaTemplate<Long, String> deadLetterOperations(ProducerFactory<Long, String> producer,
        KafkaProperties kafkaProperties) {
      final KafkaTemplate<Long, String> result = new KafkaTemplate<>(producer);
      final String defaultTopic = kafkaProperties.getTemplate().getDefaultTopic();
      if (!isEmpty(defaultTopic)) {
        result.setDefaultTopic(defaultTopic);
      }
      return result;
    }

    /**
     * Be careful, this method/Bean name is magick, see:
     * <tt>org/springframework/boot/autoconfigure/kafka/KafkaAnnotationDrivenConfiguration.java:85</tt>
     */
    @Bean
    @Profile("consumer")
    public ConcurrentKafkaListenerContainerFactory<Long, String> kafkaListenerContainerFactory(
        ConsumerFactory<Long, String> consumerFactory) {
      final ConcurrentKafkaListenerContainerFactory<Long, String> result = new ConcurrentKafkaListenerContainerFactory<>();
      result.setConsumerFactory(consumerFactory);
      return result;
    }

    @Bean
    @Profile("consumer")
    @SuppressWarnings("unchecked")
    public ConsumerFactory<Long, String> consumerFactory(KafkaProperties properties)
        throws Exception {
      final KafkaProperties.Consumer kpcProps = properties.getConsumer();
      final Class<Deserializer<Long>> keyDeserClass = (Class<Deserializer<Long>>) kpcProps
          .getKeyDeserializer();
      final Class<Deserializer<String>> valueDeserClass = (Class<Deserializer<String>>) kpcProps
          .getValueDeserializer();
      final Deserializer<Long> keyDeser = keyDeserClass.getDeclaredConstructor().newInstance();
      final Deserializer<String> valueDeser = valueDeserClass.getDeclaredConstructor()
          .newInstance();
      final DefaultKafkaConsumerFactory<Long, String> result = new DefaultKafkaConsumerFactory<>(
          properties.buildConsumerProperties());
      result.setKeyDeserializer(keyDeser);
      result.setValueDeserializer(valueDeser);
      return result;
    }

    @Bean
    public HandlerExceptionResolver sentryExceptionResolver() {
      return new io.sentry.spring.SentryExceptionResolver();
    }

    @Bean
    public ConversionService conversionService() {
      return new DefaultConversionService();
    }

  }

}