/*
 * ***********************************************************
 * Copyright, 2020, Open Raven Inc.
 * APACHE LICENSE, VERSION 2.0
 * https://www.openraven.com/legal/apache-2-license
 * *********************************************************
 */
package io.openraven.consumer.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openraven.consumer.properties.ElasticSearchServiceProperties;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ElasticSearchService {

  private final ElasticSearchServiceProperties elasticSearch;
  private final ObjectMapper mapper;
  private final Logger log = LoggerFactory.getLogger(ElasticSearchService.class);

  public ElasticSearchService(ElasticSearchServiceProperties serviceProperties,
      ObjectMapper mapper) {

    this.elasticSearch = serviceProperties;
    this.mapper = mapper;
  }

  private BasicCredentialsProvider getCredentialsProvider(String username, String password) {
    BasicCredentialsProvider provider = new BasicCredentialsProvider();
    provider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
    return provider;
  }

  private RestHighLevelClient getRestHighLevelClient() {
    RestClientBuilder client = RestClient.builder(
        new HttpHost(elasticSearch.getHost(), elasticSearch.getPort(),
            elasticSearch.getProtocol()));

    if (!(StringUtils.isEmpty(elasticSearch.getUsername()) || StringUtils
        .isEmpty(elasticSearch.getPassword()))) {
      client.setHttpClientConfigCallback(httpClientBuilder -> {
        httpClientBuilder.setDefaultCredentialsProvider(
            getCredentialsProvider(elasticSearch.getUsername(), elasticSearch.getPassword()));

        return httpClientBuilder;
      });
    }

    return new RestHighLevelClient(client);
  }

  public JsonNode getDocuments(String indexName,
      @Nullable Map<String, String> filters,
      int count) throws IOException {
    try (RestHighLevelClient client = getRestHighLevelClient()) {
      SearchRequest request = new SearchRequest(indexName);

      QueryBuilder queryBuilder;
      if (filters != null) {
        BoolQueryBuilder builder = QueryBuilders.boolQuery();

        filters.forEach((key, value) -> builder.filter(QueryBuilders.termQuery(key, value)));

        queryBuilder = builder;
      } else {
        queryBuilder = QueryBuilders.matchAllQuery();
      }

      request
          .source(SearchSourceBuilder.searchSource().size(count).query(queryBuilder));

      return mapper.convertValue(
          Arrays.stream(client.search(request, RequestOptions.DEFAULT).getHits().getHits())
              .map(SearchHit::getSourceAsMap).collect(Collectors.toList()), JsonNode.class);
    }
  }

  public JsonNode getDocument(String indexName, String documentId) throws IOException {
    try (RestHighLevelClient client = getRestHighLevelClient()) {
      return mapper.convertValue(
          client.get(new GetRequest(indexName, documentId), RequestOptions.DEFAULT).getSource(),
          JsonNode.class);
    }
  }

  public JsonNode writeDocument(String indexName, String documentId, JsonNode document)
      throws IOException {
    return updateDocument(indexName, documentId, document);
  }

  public JsonNode updateDocument(String indexName, String documentId, JsonNode document)
      throws IOException {
    try (RestHighLevelClient client = getRestHighLevelClient()) {
      UpdateRequest request = new UpdateRequest(indexName, documentId);
      request.docAsUpsert(true);
      request.doc(mapper.writeValueAsString(document), XContentType.JSON);
      request.setRefreshPolicy(RefreshPolicy.WAIT_UNTIL);

      client.update(request, RequestOptions.DEFAULT);

      return getDocument(indexName, documentId);
    }
  }

  public void deleteDocument(String indexName, String documentId) throws IOException {
    try (RestHighLevelClient client = getRestHighLevelClient()) {
      client.delete(new DeleteRequest(indexName, documentId), RequestOptions.DEFAULT);
    }
  }

  public long countDocuments(String indexName) throws IOException {
    try (RestHighLevelClient client = getRestHighLevelClient()) {
      return client.count(new CountRequest(indexName), RequestOptions.DEFAULT).getCount();
    }
  }
}
