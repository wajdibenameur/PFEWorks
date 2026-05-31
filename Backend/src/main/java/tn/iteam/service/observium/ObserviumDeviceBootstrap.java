package tn.iteam.service.observium;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import tn.iteam.config.ObserviumSnmpProperties;
import tn.iteam.domain.ObserviumDevice;
import tn.iteam.enums.DeviceStatus;
import tn.iteam.repository.ObserviumDeviceRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ObserviumDeviceBootstrap implements ApplicationRunner {

    private final ObserviumDeviceRepository deviceRepository;
    private final ObserviumSnmpProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Set<String> seedAddresses = resolveSeedAddresses();
            if (seedAddresses.isEmpty()) {
                log.info("Observium device bootstrap skipped: no seed addresses configured.");
                return;
            }

            int created = 0;
            for (String address : seedAddresses) {
                if (deviceRepository.findByIpAddress(address).isPresent()) {
                    continue;
                }

                ObserviumDevice device = new ObserviumDevice();
                device.setIpAddress(address);
                device.setHostname(address);
                device.setSnmpPort(properties.getDefaultPort());
                device.setSnmpCommunity(properties.getDefaultCommunity());
                device.setSnmpVersion(properties.getDefaultVersion());
                device.setStatus(DeviceStatus.UNKNOWN);
                device.setEnabled(true);

                deviceRepository.save(device);
                created++;
            }

            if (created > 0) {
                log.info("Observium device bootstrap inserted {} device(s).", created);
            } else {
                log.info("Observium device bootstrap found all configured addresses already present.");
            }
        } catch (DataAccessException ex) {
            // Startup should continue even if bootstrap seeding fails due to transient DB connectivity.
            log.warn("Observium device bootstrap skipped due to database access issue: {}", ex.getMessage());
        }
    }

    private Set<String> resolveSeedAddresses() {
        Set<String> addresses = new LinkedHashSet<>();

        List<String> direct = properties.getSeedAddresses();
        if (direct != null) {
            for (String raw : direct) {
                if (raw != null && !raw.isBlank()) {
                    addresses.add(raw.trim());
                }
            }
        }

        List<String> ranges = properties.getSeedRanges();
        if (ranges != null) {
            for (String rawRange : ranges) {
                addresses.addAll(expandRange(rawRange));
            }
        }
        return addresses;
    }

    private Set<String> expandRange(String rawRange) {
        Set<String> expanded = new LinkedHashSet<>();
        if (rawRange == null || rawRange.isBlank()) {
            return expanded;
        }

        String range = rawRange.trim();
        if (range.contains("/")) {
            expanded.addAll(expandCidr24(range));
            return expanded;
        }
        if (range.contains("-")) {
            expanded.addAll(expandDashRange(range));
            return expanded;
        }

        expanded.add(range);
        return expanded;
    }

    private Set<String> expandCidr24(String cidr) {
        Set<String> result = new LinkedHashSet<>();
        String[] parts = cidr.split("/");
        if (parts.length != 2 || !"24".equals(parts[1].trim())) {
            return result;
        }
        String[] octets = parts[0].trim().split("\\.");
        if (octets.length != 4) {
            return result;
        }
        String prefix = octets[0] + "." + octets[1] + "." + octets[2] + ".";
        for (int i = 1; i <= 254; i++) {
            result.add(prefix + i);
        }
        return result;
    }

    private Set<String> expandDashRange(String rawRange) {
        Set<String> result = new LinkedHashSet<>();
        String[] parts = rawRange.split("-");
        if (parts.length != 2) {
            return result;
        }

        String left = parts[0].trim();
        String right = parts[1].trim();
        String[] leftOctets = left.split("\\.");

        try {
            if (leftOctets.length == 4 && !right.contains(".")) {
                int start = Integer.parseInt(leftOctets[3]);
                int end = Integer.parseInt(right);
                String prefix = leftOctets[0] + "." + leftOctets[1] + "." + leftOctets[2] + ".";
                appendHostRange(result, prefix, start, end);
                return result;
            }

            String[] rightOctets = right.split("\\.");
            if (leftOctets.length == 4 && rightOctets.length == 4
                    && leftOctets[0].equals(rightOctets[0])
                    && leftOctets[1].equals(rightOctets[1])
                    && leftOctets[2].equals(rightOctets[2])) {
                int start = Integer.parseInt(leftOctets[3]);
                int end = Integer.parseInt(rightOctets[3]);
                String prefix = leftOctets[0] + "." + leftOctets[1] + "." + leftOctets[2] + ".";
                appendHostRange(result, prefix, start, end);
            }
        } catch (NumberFormatException ignored) {
            // Ignore malformed range fragments and keep bootstrap resilient.
        }

        return result;
    }

    private void appendHostRange(Set<String> result, String prefix, int start, int end) {
        int lower = Math.max(1, Math.min(start, end));
        int upper = Math.min(254, Math.max(start, end));
        for (int i = lower; i <= upper; i++) {
            result.add(prefix + i);
        }
    }
}
