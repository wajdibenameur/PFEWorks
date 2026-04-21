package tn.iteam.adapter.observium;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tn.iteam.client.ObserviumClientX;
import tn.iteam.domain.ApiResponse;
import tn.iteam.dto.ObserviumMetricDTO;
import tn.iteam.dto.ObserviumProblemDTO;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.exception.IntegrationDataUnavailableException;
import tn.iteam.mapper.ObserviumMapper;

import java.util.ArrayList;
import java.util.List;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class ObserviumAdapter {

    private final ObserviumClientX observiumClient;
    private final ObserviumMapper observiumMapper;

    private boolean isValid(ApiResponse<JsonNode> response) {
        return response != null
                && response.isSuccess()
                && response.getData() != null
                && response.getData().isArray();
    }

    public List<ServiceStatusDTO> fetchAll() {
        log.info("Fetching devices from Observium");
        ApiResponse<JsonNode> response = observiumClient.getDevices();
        List<ServiceStatusDTO> dtos = new ArrayList<>();
        if (!isValid(response)) {
            String errorMsg = response != null ? response.getMessage() : "null response";
            log.warn("Devices API failed: {}", errorMsg);
            throw IntegrationDataUnavailableException.forObservium("Devices unavailable: " + errorMsg);
        }
        for (JsonNode node : response.getData()) {
            dtos.add(observiumMapper.mapDeviceToDTO(node));
        }
        return dtos;
    }

    public List<ObserviumProblemDTO> fetchProblems() {
        log.info("Fetching alerts from Observium");
        ApiResponse<JsonNode> response = observiumClient.getAlerts();
        List<ObserviumProblemDTO> dtos = new ArrayList<>();
        if (!isValid(response)) {
            String errorMsg = response != null ? response.getMessage() : "null response";
            log.warn("Alerts API failed: {}", errorMsg);
            throw IntegrationDataUnavailableException.forObservium("Alerts unavailable: " + errorMsg);
        }
        for (JsonNode node : response.getData()) {
            dtos.add(observiumMapper.mapAlertToDTO(node));
        }
        return dtos;
    }

    public List<ObserviumMetricDTO> fetchMetrics() {
        List<ServiceStatusDTO> statuses = fetchAll();
        long now = Instant.now().getEpochSecond();
        List<ObserviumMetricDTO> metrics = new ArrayList<>();

        for (ServiceStatusDTO status : statuses) {
            String hostName = status.getName() != null && !status.getName().isBlank() ? status.getName() : "UNKNOWN";
            String hostId = status.getIp() != null && !status.getIp().isBlank() && !"IP_UNKNOWN".equalsIgnoreCase(status.getIp())
                    ? status.getIp()
                    : hostName;

            metrics.add(ObserviumMetricDTO.builder()
                    .hostId(hostId)
                    .hostName(hostName)
                    .itemId("device-status")
                    .metricKey("observium.device.status")
                    .value("UP".equalsIgnoreCase(status.getStatus()) ? 1.0 : 0.0)
                    .timestamp(now)
                    .ip(status.getIp())
                    .port(status.getPort())
                    .build());
        }

        return metrics;
    }
}
