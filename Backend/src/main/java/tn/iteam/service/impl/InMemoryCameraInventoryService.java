package tn.iteam.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tn.iteam.dto.CameraDeviceDTO;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.service.CameraInventoryService;

import java.util.List;

@Service
@ConditionalOnProperty(
        name = "app.db.enabled",
        havingValue = "false"
)
public class InMemoryCameraInventoryService implements CameraInventoryService {

    @Override
    public List<CameraDeviceDTO> getRegisteredCameras() {
        return List.of();
    }
}