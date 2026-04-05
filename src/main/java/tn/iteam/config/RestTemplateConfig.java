package tn.iteam.config;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

@Configuration
public class RestTemplateConfig {

    private static final Logger log = LoggerFactory.getLogger(RestTemplateConfig.class);

    // --------------------------------------------------------------
    // Bean pour ignorer SSL (ZKBio, etc.)
    // --------------------------------------------------------------
    @Bean
    public RestTemplate restTemplateIgnoringSSL() {
        log.info(" Creating RestTemplate that ignores SSL validation");
        try {
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(null, TrustAllStrategy.INSTANCE)
                    .build();

            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                    sslContext, NoopHostnameVerifier.INSTANCE);

            HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(sslSocketFactory)
                    .build();

            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .build();

            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);

            // ----------------- AJOUT DES TIMEOUTS -----------------
            factory.setReadTimeout(5000); // 5s pour la connexion
            factory.setReadTimeout(10000);   // 10s pour lecture
            factory.setConnectionRequestTimeout(5000); // 5s pour obtenir une connexion

            RestTemplate restTemplate = new RestTemplate(factory);
            restTemplate.getInterceptors().add(new LoggingInterceptor());
            log.info(" RestTemplate ignoring SSL created");
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
        log.info(" Creating default RestTemplate bean with timeouts");

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 5s pour la connexion
        factory.setReadTimeout(60000);   // 10s pour lecture

        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getInterceptors().add(new LoggingInterceptor());

        log.info(" Default RestTemplate bean created with timeouts");
        return restTemplate;
    }

}
