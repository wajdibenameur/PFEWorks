package tn.iteam.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class LoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        // Log de la requête
        log.info("\n>>> HTTP {} {}\n>>> Headers: {}\n>>> Body: {}",
                request.getMethod(), request.getURI(), request.getHeaders(),
                new String(body, StandardCharsets.UTF_8));

        // Exécution et wrapping pour buffériser la réponse
        ClientHttpResponse originalResponse = execution.execute(request, body);
        ClientHttpResponse wrappedResponse = new BufferingClientHttpResponseWrapper(originalResponse);

        // Log de la réponse (le body est maintenant disponible plusieurs fois)
        String responseBody = StreamUtils.copyToString(wrappedResponse.getBody(), StandardCharsets.UTF_8);
        log.info("\n<<< Status: {}\n<<< Headers: {}\n<<< Body: {}",
                wrappedResponse.getStatusCode(), wrappedResponse.getHeaders(), responseBody);

        // Retourner la réponse wrappée (pour que le code appelant puisse relire le body)
        return wrappedResponse;
    }
}