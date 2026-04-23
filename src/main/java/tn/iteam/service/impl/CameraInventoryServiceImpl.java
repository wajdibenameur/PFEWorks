package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.iteam.dto.CameraDeviceDTO;
import tn.iteam.repository.ServiceStatusRepository;
import tn.iteam.service.CameraInventoryService;
import tn.iteam.util.MonitoringConstants;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CameraInventoryServiceImpl implements CameraInventoryService {

    private final ServiceStatusRepository serviceStatusRepository;

    @Override
    public List<CameraDeviceDTO> getRegisteredCameras() {
        return serviceStatusRepository.findBySourceOrderByIpAscPortAsc(MonitoringConstants.SOURCE_CAMERA)
                .stream()
                .map(entity -> CameraDeviceDTO.builder()
                        .source(entity.getSource())
                        .name(entity.getName())
                        .ip(entity.getIp())
                        .port(entity.getPort())
                        .protocol(entity.getProtocol())
                        .status(entity.getStatus())
                        .category(entity.getCategory())
                        .lastScanAt(entity.getLastCheck())
                        .reachable(MonitoringConstants.STATUS_UP.equalsIgnoreCase(entity.getStatus()))
                        .persisted(true)
                        .build())
                .toList();
    }
}
