/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.data.shared;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.javatuples.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Datapoint;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsResponse;
import software.amazon.awssdk.services.cloudwatch.model.Statistic;

@Component
@Scope("singleton")
public class Utils {

  private static CloudwatchClientFunctionRunner cloudwatchClientProvider;

  @Autowired
  public void setCloudwatchClientProvider(CloudwatchClientFunctionRunner cloudwatchClientProvider) {
    Utils.cloudwatchClientProvider = cloudwatchClientProvider;
  }

  public static Pair<Long, GetMetricStatisticsResponse> getCloudwatchMetricMinimum(
      String regionID, String namespace, String metric,
      List<Dimension> dimensions, AwsCredentialsProvider credentialsProvider) {

    final CloudWatchClient client = cloudwatchClientProvider
        .createClient(regionID, credentialsProvider);

    Instant startTS = Instant.now().minus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
    Instant endTS = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES);

    GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder().startTime(startTS)
        .endTime(endTS)
        .namespace(namespace).period(3600).metricName(metric).statistics(Statistic.MINIMUM)
        .dimensions(dimensions).build();

    GetMetricStatisticsResponse getMetricStatisticsResult = client.getMetricStatistics(request);

    return Pair.with(getMetricStatisticsResult.datapoints().stream().map(Datapoint::minimum)
        .map(Double::longValue)
        // we should only get one metric, as we're asking for MINIMUM, so pick
        // that value
        .findFirst().orElse(0L), getMetricStatisticsResult);
  }

  public static Pair<Double, GetMetricStatisticsResponse> getCloudwatchDoubleMetricMinimum(
      String regionID, String namespace, String metric,
      List<Dimension> dimensions, AwsCredentialsProvider credentialsProvider) {

    final CloudWatchClient client = cloudwatchClientProvider
        .createClient(regionID, credentialsProvider);

    Instant startTS = Instant.now().minus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
    Instant endTS = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES);

    GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder().startTime(startTS)
        .endTime(endTS)
        .namespace(namespace).period(3600).metricName(metric).statistics(Statistic.MINIMUM)
        .dimensions(dimensions).build();

    GetMetricStatisticsResponse getMetricStatisticsResult = client.getMetricStatistics(request);

    return Pair.with(getMetricStatisticsResult.datapoints().stream().map(Datapoint::minimum)
        // we should only get one metric, as we're asking for MINIMUM, so pick
        // that value
        .findFirst().orElse(0D), getMetricStatisticsResult);
  }

}
