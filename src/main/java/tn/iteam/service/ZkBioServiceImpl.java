package tn.iteam.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.iteam.adapter.zkbio.ZkBioAdapter;
import tn.iteam.client.ZkBioClientX;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.dto.ZkBioAttendanceDTO;
import tn.iteam.dto.ZkBioProblemDTO;
import tn.iteam.mapper.ZkBioAttendanceMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Business service for ZKBio operations only.
 * No orchestration, no snapshot publication, no integration refresh logic here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZkBioServiceImpl implements ZkBioServiceInterface {

    private final ZkBioAdapter zkBioAdapter;
    private final ZkBioClientX zkBioClient;
    private final ZkBioAttendanceMapper attendanceMapper;

    @Override
    public ServiceStatusDTO getServerStatus() {
        log.info("Fetching ZKBio server status");
        try {
            JsonNode status = zkBioClient.getStatus();
            URI baseUri = zkBioClient.getBaseUri();

            ServiceStatusDTO dto = new ServiceStatusDTO();
            dto.setSource("ZKBIO");
            dto.setName("ZKBio Server");
            dto.setIp(baseUri != null ? baseUri.getHost() : null);
            dto.setPort(resolvePort(baseUri));
            dto.setProtocol(baseUri != null ? normalizeProtocol(baseUri.getScheme()) : null);
            dto.setCategory("ACCESS_CONTROL");
            dto.setStatus(status != null && status.has("code") && status.get("code").asInt() == 1 ? "UP" : "DOWN");
            return dto;
        } catch (Exception e) {
            log.warn("ZKBio status degraded: {}", e.getMessage());

            ServiceStatusDTO dto = new ServiceStatusDTO();
            dto.setSource("ZKBIO");
            dto.setName("ZKBio Server");
            dto.setStatus("DOWN");
            return dto;
        }
    }

    @Override
    public List<ServiceStatusDTO> fetchDevices() {
        log.info("Fetching ZKBio devices");
        return zkBioAdapter.fetchAll();
    }

    @Override
    public List<ZkBioProblemDTO> fetchProblems() {
        log.info("Fetching ZKBio problems/alerts");
        return zkBioAdapter.fetchProblems();
    }

    @Override
    public List<ZkBioAttendanceDTO> fetchAttendanceLogs() {
        log.info("Fetching ZKBio attendance logs");
        return mapAttendanceLogs(zkBioClient.getAttendanceLogs());
    }

    @Override
    public List<ZkBioAttendanceDTO> fetchAttendanceLogs(long startTime, long endTime) {
        log.info("Fetching ZKBio attendance logs from {} to {}", startTime, endTime);
        return mapAttendanceLogs(zkBioClient.getAttendanceLogs(startTime, endTime));
    }

    @Override
    public List<ZkBioAttendanceDTO> fetchUsers() {
        log.info("Fetching ZKBio users");
        List<ZkBioAttendanceDTO> dtos = new ArrayList<>();

        try {
            JsonNode users = zkBioClient.getUsers();

            if (users == null || !users.isArray()) {
                log.warn("No users received from ZKBio");
                return dtos;
            }

            for (JsonNode userNode : users) {
                ZkBioAttendanceDTO dto = ZkBioAttendanceDTO.builder()
                        .userId(userNode.has("user_id") ? userNode.get("user_id").asText() : "")
                        .userName(userNode.has("name") ? userNode.get("name").asText() : "")
                        .source("ZKBIO")
                        .build();
                dtos.add(dto);
            }

            log.info("Fetched {} users from ZKBio", dtos.size());
        } catch (Exception e) {
            log.warn("ZKBio users unavailable: {}", e.getMessage());
        }

        return dtos;
    }

    private List<ZkBioAttendanceDTO> mapAttendanceLogs(JsonNode logs) {
        List<ZkBioAttendanceDTO> dtos = new ArrayList<>();

        try {
            if (logs == null || !logs.isArray()) {
                log.warn("No attendance logs received from ZKBio");
                return dtos;
            }

            for (JsonNode logNode : logs) {
                dtos.add(attendanceMapper.mapToDTO(logNode));
            }

            log.info("Fetched {} attendance logs from ZKBio", dtos.size());
        } catch (Exception e) {
            log.warn("ZKBio attendance logs unavailable: {}", e.getMessage());
        }

        return dtos;
    }

    private Integer resolvePort(URI baseUri) {
        if (baseUri == null) {
            return null;
        }
        if (baseUri.getPort() > 0) {
            return baseUri.getPort();
        }
        return "https".equalsIgnoreCase(baseUri.getScheme()) ? 443 : 80;
    }

    private String normalizeProtocol(String scheme) {
        return scheme == null ? null : scheme.toUpperCase();
    }
}
