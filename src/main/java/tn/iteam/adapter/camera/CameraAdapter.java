package tn.iteam.adapter.camera;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.util.MonitoringConstants;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
public class CameraAdapter {

    private static final Logger log = LoggerFactory.getLogger(CameraAdapter.class);
    private static final String CAMERA_NAME_PREFIX = "Camera-";
    private static final int CONNECT_TIMEOUT_MS = 150;
    private static final int MAX_WORKERS = 32;

    private final SourceAvailabilityService availabilityService;

    public CameraAdapter(SourceAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    public List<ServiceStatusDTO> fetchAll(String subnet, List<Integer> ports) {
        return fetchAll(List.of(subnet), ports);
    }

    public List<ServiceStatusDTO> fetchAll(List<String> subnets, List<Integer> ports) {
        List<String> sanitizedSubnets = subnets == null ? List.of() : subnets.stream()
                .filter(subnet -> subnet != null && !subnet.isBlank())
                .map(String::trim)
                .toList();
        if (sanitizedSubnets.isEmpty()) {
            return List.of();
        }

        log.info("Scanning cameras in subnets {} using ports {}", sanitizedSubnets, ports);
        List<ServiceStatusDTO> list = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(MAX_WORKERS);

        try {
            List<Future<Optional<ServiceStatusDTO>>> futures = new ArrayList<>();
            for (String subnet : sanitizedSubnets) {
                for (int i = 1; i <= 254; i++) {
                    String ip = subnet + "." + i;
                    futures.add(executor.submit(() -> scanIp(ip, ports)));
                }
            }

            for (Future<Optional<ServiceStatusDTO>> future : futures) {
                try {
                    future.get().ifPresent(list::add);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Camera scan interrupted", exception);
                } catch (ExecutionException exception) {
                    log.debug("Camera scan task failed: {}", exception.getMessage());
                }
            }

            list.sort(Comparator.comparing(ServiceStatusDTO::getIp, Comparator.nullsLast(String::compareTo)));
            availabilityService.markAvailable(MonitoringConstants.SOURCE_CAMERA);
            if (list.isEmpty()) {
                log.warn("No cameras detected in subnets {}", sanitizedSubnets);
            }
            return list;
        } catch (Exception e) {
            availabilityService.markUnavailable(MonitoringConstants.SOURCE_CAMERA, e.getMessage());
            log.error("Unexpected error while scanning cameras in subnets {}", sanitizedSubnets, e);
            return list;
        } finally {
            executor.shutdownNow();
        }
    }

    private Optional<ServiceStatusDTO> scanIp(String ip, List<Integer> ports) {
        for (Integer port : ports) {
            if (port == null || port <= 0) {
                continue;
            }
            if (isPortOpen(ip, port)) {
                ServiceStatusDTO dto = new ServiceStatusDTO();
                dto.setSource(MonitoringConstants.SOURCE_CAMERA);
                dto.setName(CAMERA_NAME_PREFIX + ip);
                dto.setIp(ip);
                dto.setPort(port);
                dto.setProtocol(resolveProtocol(port));
                dto.setStatus(MonitoringConstants.STATUS_UP);
                dto.setCategory(MonitoringConstants.CATEGORY_CAMERA);

                log.info("Camera detected at {} on port {}", ip, port);
                return Optional.of(dto);
            }
        }
        return Optional.empty();
    }

    private boolean isPortOpen(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String resolveProtocol(int port) {
        return switch (port) {
            case 554 -> MonitoringConstants.PROTOCOL_RTSP;
            case 37777 -> "TCP";
            default -> "TCP";
        };
    }
}
