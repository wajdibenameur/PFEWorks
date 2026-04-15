package tn.iteam.adapter.camera;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.service.SourceAvailabilityService;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

@Component
public class CameraAdapter {

    private static final Logger log = LoggerFactory.getLogger(CameraAdapter.class);

    private final SourceAvailabilityService availabilityService;

    public CameraAdapter(SourceAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    public List<ServiceStatusDTO> fetchAll(String subnet) {
        log.info("Scanning cameras in subnet {}", subnet);
        List<ServiceStatusDTO> list = new ArrayList<>();

        try {
            for (int i = 1; i <= 254; i++) {
                String ip = subnet + "." + i;

                if (isPortOpen(ip, 554)) {
                    ServiceStatusDTO dto = new ServiceStatusDTO();
                    dto.setSource("CAMERA");
                    dto.setName("Camera-" + ip);
                    dto.setIp(ip);
                    dto.setPort(554);
                    dto.setProtocol("RTSP");
                    dto.setStatus("UP");
                    dto.setCategory("CAMERA");
                    list.add(dto);

                    log.info("Camera detected at {}", ip);
                }
            }

            availabilityService.markAvailable("CAMERA");
            if (list.isEmpty()) {
                log.warn("No cameras detected in subnet {}", subnet);
            }
            return list;
        } catch (Exception e) {
            availabilityService.markUnavailable("CAMERA", e.getMessage());
            log.error("Unexpected error while scanning cameras in subnet {}", subnet, e);
            return list;
        }
    }

    private boolean isPortOpen(String ip, int port) {
        try (Socket socket = new Socket(ip, port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
