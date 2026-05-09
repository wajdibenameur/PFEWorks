package tn.iteam.adapter.zkbio;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.iteam.client.ZkBioClientX;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.dto.ZkBioMetricDTO;
import tn.iteam.dto.ZkBioProblemDTO;
import tn.iteam.exception.IntegrationException;
import tn.iteam.mapper.ZkBioMapper;
import tn.iteam.util.MonitoringConstants;

import java.time.Instant;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ZkBioAdapter {

    private static final Logger log = LoggerFactory.getLogger(ZkBioAdapter.class);
    private static final String SERVER_NAME = "ZKBio Server";
    private static final String SERVER_HOST_ID = "ZKBIO_SERVER";
    private static final String UNKNOWN_IP = "IP_UNKNOWN";
    private static final String CHECKING_SERVER_MESSAGE = "Checking ZKBio server";
    private static final String SERVER_UP_MESSAGE = "ZKBio Server is UP";
    private static final String DEVICES_API_NULL_MESSAGE = "ZKBio Server devices API returned null";
    private static final String FETCHING_PROBLEMS_MESSAGE = "Fetching problems from ZKBio";
    private static final String NO_ALERTS_MESSAGE = "No alerts received from ZKBio";
    private static final String PROBLEMS_FETCHED_MESSAGE = "{} problems fetched from ZKBio";

    private final ZkBioClientX zkBioClient;
    private final ZkBioMapper zkBioMapper;

    public List<ServiceStatusDTO> fetchAll() {
        log.info(CHECKING_SERVER_MESSAGE);

        List<ServiceStatusDTO> dtos = new ArrayList<>();
        ServiceStatusDTO dto = baseServerStatus();

        try {
            if (zkBioClient.getDevices() != null) {
                dto.setStatus(MonitoringConstants.STATUS_UP);
                log.info(SERVER_UP_MESSAGE);
            } else {
                log.warn(DEVICES_API_NULL_MESSAGE);
            }
        } catch (IntegrationException e) {
            log.warn("ZKBio unavailable: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error connecting to ZKBio Server", e);
        }

        dtos.add(dto);
        return dtos;
    }

    public List<ZkBioProblemDTO> fetchProblems() {
        log.info(FETCHING_PROBLEMS_MESSAGE);

        List<ZkBioProblemDTO> dtos = new ArrayList<>();
        JsonNode alerts = fetchAlerts("ZKBio problems unavailable: {}");

        if (alerts == null || !alerts.isArray()) {
            log.warn(NO_ALERTS_MESSAGE);
            return dtos;
        }

        for (JsonNode alertNode : alerts) {
            dtos.add(zkBioMapper.mapAlertToDTO(alertNode));
        }

        log.info(PROBLEMS_FETCHED_MESSAGE, dtos.size());
        return dtos;
    }

    private JsonNode fetchAlerts(String unavailableLogTemplate) {
        try {
            return zkBioClient.getAlerts();
        } catch (IntegrationException e) {
            log.warn(unavailableLogTemplate, e.getMessage());
            return null;
        }
    }

    public List<ZkBioMetricDTO> fetchMetrics() {
        long now = Instant.now().getEpochSecond();
        List<ZkBioMetricDTO> metrics = new ArrayList<>();
        ServiceStatusDTO server = baseServerStatus();

        JsonNode devices;
        try {
            devices = zkBioClient.getDevices();
        } catch (IntegrationException e) {
            log.warn("ZKBio metrics unavailable: {}", e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.error("Unexpected error while fetching ZKBio metrics", e);
            return List.of();
        }

        if (devices == null || !devices.isArray()) {
            log.warn("ZKBio devices API unavailable or invalid, skipping metrics persistence");
            return List.of();
        }

        int deviceCount = devices.size();

        metrics.add(ZkBioMetricDTO.builder()
                .hostId(SERVER_HOST_ID)
                .hostName(SERVER_NAME)
                .itemId("devices-count")
                .metricKey("zkbio.devices.count")
                .value((double) deviceCount)
                .timestamp(now)
                .ip(server.getIp())
                .port(server.getPort())
                .build());

        metrics.add(ZkBioMetricDTO.builder()
                .hostId(SERVER_HOST_ID)
                .hostName(SERVER_NAME)
                .itemId("server-status")
                .metricKey("zkbio.server.status")
                .value(deviceCount > 0 ? 1.0 : 0.0)
                .timestamp(now)
                .ip(server.getIp())
                .port(server.getPort())
                .build());

        return metrics;
    }

    private ServiceStatusDTO baseServerStatus() {
        URI baseUri = zkBioClient.getBaseUri();

        ServiceStatusDTO dto = new ServiceStatusDTO();
        dto.setSource(MonitoringConstants.SOURCE_ZKBIO);
        dto.setName(SERVER_NAME);
        dto.setIp(baseUri != null && baseUri.getHost() != null ? baseUri.getHost() : UNKNOWN_IP);
        dto.setPort(resolvePort(baseUri));
        dto.setProtocol(resolveProtocol(baseUri));
        dto.setCategory(MonitoringConstants.CATEGORY_ACCESS);
        dto.setStatus(MonitoringConstants.STATUS_DOWN);
        return dto;
    }

    private Integer resolvePort(URI baseUri) {
        if (baseUri == null) {
            return 443;
        }
        if (baseUri.getPort() > 0) {
            return baseUri.getPort();
        }
        return "https".equalsIgnoreCase(baseUri.getScheme()) ? 443 : 80;
    }

    private String resolveProtocol(URI baseUri) {
        if (baseUri == null || baseUri.getScheme() == null || baseUri.getScheme().isBlank()) {
            return MonitoringConstants.PROTOCOL_HTTPS;
        }
        return baseUri.getScheme().toUpperCase();
    }
}
