package tn.iteam.service.camera;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.config.CameraMonitoringProperties;
import tn.iteam.domain.CameraDevice;
import tn.iteam.enums.DeviceStatus;
import tn.iteam.repository.CameraDeviceRepository;
import tn.iteam.service.support.DatabasePersistenceGuard;
import tn.iteam.util.MonitoringConstants;

import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CameraInventorySeeder {

    private final CameraMonitoringProperties properties;
    private final CameraDeviceRepository cameraDeviceRepository;
    private final DatabasePersistenceGuard databasePersistenceGuard;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedIfMissing() {
        Set<CameraSeedEntry> generated = generateEntries();
        if (generated.isEmpty()) {
            log.warn("Camera seeding skipped: no valid camera.ranges configured");
            return;
        }

        int created = 0;
        Integer defaultPort = properties.getPorts().stream().filter(p -> p != null && p > 0).findFirst().orElse(null);
        for (CameraSeedEntry entry : generated) {
            if (databasePersistenceGuard.safeLoad(
                    MonitoringConstants.SOURCE_CAMERA,
                    "camera-seed-check",
                    () -> cameraDeviceRepository.findByIpAddress(entry.ipAddress()),
                    java.util.Optional.empty()
            ).isPresent()) {
                continue;
            }
            boolean persisted = databasePersistenceGuard.safeRun(
                    MonitoringConstants.SOURCE_CAMERA,
                    "camera-seed-save",
                    () -> cameraDeviceRepository.save(CameraDevice.builder()
                            .name("Camera " + entry.ipAddress())
                            .type("IP_CAMERA")
                            .ipAddress(entry.ipAddress())
                            .subnet(entry.subnet())
                            .port(defaultPort)
                            .status(DeviceStatus.UNKNOWN)
                            .enabled(Boolean.TRUE)
                            .build())
            );
            if (!persisted) {
                log.warn("Camera inventory seeding paused because database is unavailable");
                break;
            }
            created++;
        }
        log.info("Camera inventory seeding completed: ranges={} generated={} created={}",
                properties.getRanges(), generated.size(), created);
    }

    private Set<CameraSeedEntry> generateEntries() {
        Set<CameraSeedEntry> result = new LinkedHashSet<>();
        for (String rawRange : properties.getRanges()) {
            CameraRange parsed = CameraRange.parse(rawRange);
            if (parsed == null) {
                log.warn("Ignoring invalid camera range '{}'", rawRange);
                continue;
            }
            for (int host = parsed.startHost(); host <= parsed.endHost(); host++) {
                String ip = parsed.subnetPrefix() + "." + host;
                result.add(new CameraSeedEntry(ip, parsed.subnetPrefix()));
            }
        }
        return result;
    }

    private record CameraSeedEntry(String ipAddress, String subnet) {
    }

    private record CameraRange(String subnetPrefix, int startHost, int endHost) {
        private static CameraRange parse(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String normalized = value.trim();
            int lastDot = normalized.lastIndexOf('.');
            if (lastDot <= 0 || lastDot == normalized.length() - 1) {
                return null;
            }

            String subnet = normalized.substring(0, lastDot);
            String hostRange = normalized.substring(lastDot + 1);
            String[] bounds = hostRange.split("-", 2);
            if (bounds.length != 2) {
                return null;
            }

            try {
                int start = Integer.parseInt(bounds[0]);
                int end = Integer.parseInt(bounds[1]);
                if (start < 1 || end > 254 || start > end) {
                    return null;
                }
                return new CameraRange(subnet, start, end);
            } catch (NumberFormatException exception) {
                return null;
            }
        }
    }
}
