package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tn.iteam.domain.CameraDevice;
import tn.iteam.dto.CameraDeviceDTO;
import tn.iteam.repository.CameraDeviceRepository;
import tn.iteam.service.CameraInventoryService;
import tn.iteam.service.camera.CameraHealthPollingService;

import java.util.Comparator;
import java.util.List;

@Service
@ConditionalOnProperty(
        name = "app.db.enabled",
        havingValue = "true",
        matchIfMissing = true
)
@RequiredArgsConstructor
public class CameraInventoryServiceImpl implements CameraInventoryService {

    private final CameraDeviceRepository cameraDeviceRepository;
    private final CameraHealthPollingService cameraHealthPollingService;

    @Override
    public List<CameraDeviceDTO> getRegisteredCameras() {
        return cameraDeviceRepository.findAll().stream()
                .sorted(Comparator.comparing(CameraDevice::getIpAddress, Comparator.nullsLast(String::compareTo))
                        .thenComparing(CameraDevice::getPort, Comparator.nullsLast(Integer::compareTo)))
                .map(cameraHealthPollingService::toDto)
                .toList();
    }
}
