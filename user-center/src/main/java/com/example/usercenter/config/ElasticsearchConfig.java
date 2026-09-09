package com.example.usercenter.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContexts;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.apache.commons.lang3.StringUtils;

/**
 * Elasticsearch 9.x 客户端配置
 */
@Configuration
public class ElasticsearchConfig {

    @Value("${elasticsearch.host:localhost}")
    private String host;

    @Value("${elasticsearch.port:9200}")
    private int port;

    @Value("${elasticsearch.scheme:http}")
    private String scheme;

    @Value("${elasticsearch.username:}")
    private String username;

    @Value("${elasticsearch.password:}")
    private String password;

    @Value("${elasticsearch.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${elasticsearch.socket-timeout-ms:30000}")
    private int socketTimeoutMs;

    @Value("${elasticsearch.connection-request-timeout-ms:5000}")
    private int connectionRequestTimeoutMs;

    @Value("${elasticsearch.max-conn-total:100}")
    private int maxConnTotal;

    @Value("${elasticsearch.max-conn-per-route:20}")
    private int maxConnPerRoute;

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        RestClientBuilder builder = RestClient.builder(new HttpHost(host, port, scheme));

        builder.setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                .setConnectTimeout(connectTimeoutMs)
                .setSocketTimeout(socketTimeoutMs)
                .setConnectionRequestTimeout(connectionRequestTimeoutMs));

        BasicCredentialsProvider credentialsProvider = null;

        // 有认证信息时配置
        if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password));
        }

        BasicCredentialsProvider finalCredentialsProvider = credentialsProvider;
        builder.setHttpClientConfigCallback(httpClientBuilder -> {
            httpClientBuilder.setMaxConnTotal(maxConnTotal);
            httpClientBuilder.setMaxConnPerRoute(maxConnPerRoute);
            if (finalCredentialsProvider != null) {
                httpClientBuilder.setDefaultCredentialsProvider(finalCredentialsProvider);
            }
            return httpClientBuilder;
        });

        RestClient restClient = builder.build();
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
