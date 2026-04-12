package tn.iteam.adapter.camera;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.iteam.dto.ServiceStatusDTO;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

@Component
public class CameraAdapter {

    private static final Logger log = LoggerFactory.getLogger(CameraAdapter.class);

    // fetchAll() pour uniformité avec Zabbix/Observium/ZkBio
    public List<ServiceStatusDTO> fetchAll(String subnet) {

        log.info("📷 Scanning cameras in subnet {}", subnet);
        List<ServiceStatusDTO> list = new ArrayList<>();

        for (int i = 1; i <= 254; i++) {  // scan complet du subnet
            String ip = subnet + "." + i;

            if (isPortOpen(ip, 554)) {
                ServiceStatusDTO dto = new ServiceStatusDTO();
                dto.setSource("CAMERA");
                dto.setName("Camera-" + ip); // plus tard ONVIF/Dahua SDK pour vrai nom
                dto.setIp(ip);
                dto.setPort(554);
                dto.setProtocol("RTSP");
                dto.setStatus("UP");
                dto.setCategory("CAMERA");
                list.add(dto);

                log.info("📸 Camera detected at {}", ip);
            }
        }

        if (list.isEmpty()) {
            log.warn("⚠️ No cameras detected in subnet {}", subnet);
        }

        return list;
    }

    private boolean isPortOpen(String ip, int port) {
        try (Socket socket = new Socket(ip, port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
