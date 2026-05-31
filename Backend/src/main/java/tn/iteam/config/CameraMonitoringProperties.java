package tn.iteam.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "camera")
public class CameraMonitoringProperties {

    private List<String> ranges = new ArrayList<>();
    private List<Integer> ports = new ArrayList<>(List.of(554, 37777, 80, 8080, 8000));
    private int connectTimeoutMs = 500;
    private long pollIntervalMs = 30000L;
    private int pollMaxWorkers = 32;
}

