package tn.iteam.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

@Configuration
public class RestTemplateConfig {

    private static final Logger log = LoggerFactory.getLogger(RestTemplateConfig.class);

    @Value("${integration.http.connect-timeout-ms:5000}")
    private long connectTimeoutMs;

    @Value("${integration.http.read-timeout-ms:10000}")
    private long readTimeoutMs;

    private RequestConfig buildRequestConfig() {
        return RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                .setResponseTimeout(Timeout.ofMilliseconds(readTimeoutMs))
                .build();
    }

    // --------------------------------------------------------------
    // Bean pour ignorer SSL (ZKBio, etc.)
    // --------------------------------------------------------------
    @Bean
    public RestTemplate restTemplateIgnoringSSL() {
        log.info("Creating RestTemplate that ignores SSL validation");

        try {
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(null, TrustAllStrategy.INSTANCE)
                    .build();

            SSLConnectionSocketFactory socketFactory =
                    new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);

            HttpClientConnectionManager connectionManager =
                    PoolingHttpClientConnectionManagerBuilder.create()
                            .setSSLSocketFactory(socketFactory)
                            .build();

            RequestConfig requestConfig = buildRequestConfig();

            CloseableHttpClient httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .setConnectionManager(connectionManager)
                    .build();

            HttpComponentsClientHttpRequestFactory factory =
                    new HttpComponentsClientHttpRequestFactory(httpClient);

            factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));

            RestTemplate restTemplate = new RestTemplate(factory);
            restTemplate.getInterceptors().add(new LoggingInterceptor());

            log.info("RestTemplate ignoring SSL created");
            return restTemplate;

        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            throw new RuntimeException("Failed to create SSL-disabled RestTemplate", e);
        }
    }

    // --------------------------------------------------------------
    // Bean principal (utilisé par défaut)
    // --------------------------------------------------------------
    @Bean
    @Primary
    public RestTemplate restTemplate() {
        log.info("Creating default RestTemplate bean with timeouts");

        RequestConfig requestConfig = buildRequestConfig();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();

        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));

        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getInterceptors().add(new LoggingInterceptor());

        log.info("Default RestTemplate bean created with timeouts");
        return restTemplate;
    }
}
