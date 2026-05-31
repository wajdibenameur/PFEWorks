package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.iteam.domain.ObserviumInterface;
import tn.iteam.dto.InterfaceDTO;
import tn.iteam.dto.InterfaceMetricsDTO;
import tn.iteam.repository.ObserviumInterfaceRepository;
import tn.iteam.service.ObserviumInterfaceService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ObserviumInterfaceServiceImpl implements ObserviumInterfaceService {

    private final ObserviumInterfaceRepository repository;

    @Override
    public List<InterfaceDTO> getAllInterfaces() {
        return repository.findAll().stream()
                .sorted(java.util.Comparator
                        .comparing(ObserviumInterface::getHostId, java.util.Comparator.nullsLast(String::compareTo))
                        .thenComparing(ObserviumInterface::getIfIndex, java.util.Comparator.nullsLast(Integer::compareTo)))
                .map(this::toDto)
                .toList();
    }

    private InterfaceDTO toDto(ObserviumInterface entity) {
        return InterfaceDTO.builder()
                .hostId(entity.getHostId())
                .ipAddress(entity.getIpAddress())
                .ifIndex(entity.getIfIndex())
                .name(entity.getName())
                .adminStatus(entity.getAdminStatus())
                .operStatus(entity.getOperStatus())
                .speedBps(entity.getSpeedBps())
                .metrics(InterfaceMetricsDTO.builder()
                        .inBandwidthMbps(entity.getInBandwidthMbps())
                        .outBandwidthMbps(entity.getOutBandwidthMbps())
                        .utilizationPercent(entity.getUtilizationPercent())
                        .inOctets(entity.getInOctets())
                        .outOctets(entity.getOutOctets())
                        .inErrors(entity.getInErrors())
                        .outErrors(entity.getOutErrors())
                        .build())
                .build();
    }
}
