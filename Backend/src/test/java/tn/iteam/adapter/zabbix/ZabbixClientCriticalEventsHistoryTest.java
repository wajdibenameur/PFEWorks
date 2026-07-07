package tn.iteam.adapter.zabbix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ZabbixClientCriticalEventsHistoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getCriticalEventsHistoryBuildsExpectedEventGetPayload() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorizationHeader = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api_jsonrpc.php", exchange -> handleRequest(exchange, requestBody, authorizationHeader));
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();

        try {
            WebClient webClient = WebClient.builder().build();
            ZabbixClient client = new ZabbixClient(webClient, webClient, objectMapper);
            ReflectionTestUtils.setField(
                    client,
                    "zabbixUrl",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/api_jsonrpc.php"
            );
            ReflectionTestUtils.setField(client, "apiToken", "secret-token");

            JsonNode result = client.getCriticalEventsHistory(100L, 200L).block();

            assertThat(result).isNotNull();
            assertThat(result.isArray()).isTrue();
            assertThat(authorizationHeader.get()).isEqualTo("Bearer secret-token");

            JsonNode payload = objectMapper.readTree(requestBody.get());
            assertThat(payload.path("method").asText()).isEqualTo("event.get");
            assertThat(payload.path("params").path("source").asInt()).isEqualTo(0);
            assertThat(payload.path("params").path("object").asInt()).isEqualTo(0);
            assertThat(payload.path("params").path("time_from").asLong()).isEqualTo(100L);
            assertThat(payload.path("params").path("time_till").asLong()).isEqualTo(200L);
            assertThat(payload.path("params").path("limit").asInt()).isEqualTo(5000);
            assertThat(payload.path("params").path("sortfield").asText()).isEqualTo("clock");
            assertThat(payload.path("params").path("sortorder").asText()).isEqualTo("DESC");
            assertThat(payload.path("params").path("severities").isArray()).isTrue();
            assertThat(payload.path("params").path("severities").get(0).asInt()).isEqualTo(4);
            assertThat(payload.path("params").path("severities").get(1).asInt()).isEqualTo(5);
        } finally {
            server.stop(0);
        }
    }

    private void handleRequest(
            HttpExchange exchange,
            AtomicReference<String> requestBody,
            AtomicReference<String> authorizationHeader
    ) throws IOException {
        authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
        try (InputStream body = exchange.getRequestBody()) {
            requestBody.set(new String(body.readAllBytes(), StandardCharsets.UTF_8));
        }

        byte[] response = "{\"jsonrpc\":\"2.0\",\"result\":[]}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
