package tn.iteam.service.camera;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.iteam.config.CameraMonitoringProperties;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class CameraReachabilityService {

    private final CameraMonitoringProperties properties;

    public CameraProbeResult probe(String ip, List<Integer> candidatePorts) {
        int timeoutMs = Math.max(250, properties.getConnectTimeoutMs());
        List<String> attemptLogs = new ArrayList<>();

        for (Integer port : candidatePorts) {
            if (port == null || port <= 0) {
                continue;
            }
            boolean reachable = isTcpReachable(ip, port, timeoutMs, attemptLogs);
            if (reachable) {
                CameraProbeResult result = CameraProbeResult.up(ip, port, timeoutMs, "TCP_CONNECT", attemptLogs);
                logProbeResult(result);
                return result;
            }
        }

        if (properties.isCommandPingEnabled()) {
            boolean pingReachable = isCommandPingReachable(ip, timeoutMs, attemptLogs);
            if (pingReachable) {
                CameraProbeResult result = CameraProbeResult.up(ip, null, timeoutMs, "ICMP_COMMAND", attemptLogs);
                logProbeResult(result);
                return result;
            }
        }

        CameraProbeResult result = CameraProbeResult.down(ip, timeoutMs, attemptLogs);
        logProbeResult(result);
        return result;
    }

    private boolean isTcpReachable(String ip, int port, int timeoutMs, List<String> attemptLogs) {
        long startedAt = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
            long durationMs = nanosToMillis(System.nanoTime() - startedAt);
            attemptLogs.add("TCP " + ip + ":" + port + " reachable in " + durationMs + " ms");
            return true;
        } catch (Exception exception) {
            long durationMs = nanosToMillis(System.nanoTime() - startedAt);
            attemptLogs.add("TCP " + ip + ":" + port + " failed in " + durationMs + " ms -> " + safeMessage(exception));
            return false;
        }
    }

    private boolean isCommandPingReachable(String ip, int timeoutMs, List<String> attemptLogs) {
        List<String> command = buildPingCommand(ip, timeoutMs);
        if (command.isEmpty()) {
            attemptLogs.add("ICMP command ping unsupported on current OS");
            return false;
        }

        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().reduce("", (left, right) -> left + right + System.lineSeparator());
            }
            int exitCode = process.waitFor();
            attemptLogs.add("PING " + ip + " exitCode=" + exitCode + " output=" + sanitizeOutput(output));
            return exitCode == 0;
        } catch (Exception exception) {
            attemptLogs.add("PING " + ip + " failed -> " + safeMessage(exception));
            return false;
        }
    }

    private List<String> buildPingCommand(String ip, int timeoutMs) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return List.of("ping", "-n", "1", "-w", Integer.toString(timeoutMs), ip);
        }
        if (os.contains("linux")) {
            int timeoutSeconds = Math.max(1, (int) Math.ceil(timeoutMs / 1000.0));
            return List.of("ping", "-c", "1", "-W", Integer.toString(timeoutSeconds), ip);
        }
        if (os.contains("mac")) {
            int timeoutSeconds = Math.max(1, (int) Math.ceil(timeoutMs / 1000.0));
            return List.of("ping", "-c", "1", "-t", Integer.toString(timeoutSeconds), ip);
        }
        return List.of();
    }

    private void logProbeResult(CameraProbeResult result) {
        if (!properties.isLogProbeDetails()) {
            return;
        }
        log.info(
                "Camera probe ip={} timeoutMs={} method={} reachable={} selectedPort={} details={}",
                result.ip(),
                result.timeoutMs(),
                result.method(),
                result.reachable(),
                result.selectedPort(),
                result.attemptLogs()
        );
    }

    private String sanitizeOutput(String output) {
        if (output == null) {
            return "";
        }
        String normalized = output.replaceAll("\\s+", " ").trim();
        return normalized.length() > 240 ? normalized.substring(0, 240) + "..." : normalized;
    }

    private long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return throwable != null ? throwable.getClass().getSimpleName() : "unknown";
        }
        return throwable.getMessage();
    }

    public record CameraProbeResult(
            String ip,
            Integer selectedPort,
            int timeoutMs,
            boolean reachable,
            String method,
            List<String> attemptLogs
    ) {
        public static CameraProbeResult up(String ip, Integer selectedPort, int timeoutMs, String method, List<String> attemptLogs) {
            return new CameraProbeResult(ip, selectedPort, timeoutMs, true, method, List.copyOf(attemptLogs));
        }

        public static CameraProbeResult down(String ip, int timeoutMs, List<String> attemptLogs) {
            return new CameraProbeResult(ip, null, timeoutMs, false, "NONE", List.copyOf(attemptLogs));
        }
    }
}
