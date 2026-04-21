package tn.iteam.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ObserviumClientXFallbackTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void normalizeObjectCollectionTransformsObjectValuesToArray() {
        ObserviumClientX observiumClientX = new ObserviumClientX(
                mock(WebClient.class),
                OBJECT_MAPPER,
                "http://observium.local",
                "token"
        );

        JsonNode objectCollection = OBJECT_MAPPER.createObjectNode()
                .set("1", OBJECT_MAPPER.createObjectNode().put("device", "sw1"));

        JsonNode normalized = (JsonNode) ReflectionTestUtils.invokeMethod(
                observiumClientX,
                "normalizeObjectCollection",
                objectCollection
        );

        assertThat(normalized).isNotNull();
        assertThat(normalized.isArray()).isTrue();
        assertThat(normalized).hasSize(1);
        assertThat(normalized.get(0).get("device").asText()).isEqualTo("sw1");
    }
}
