package tn.iteam.service;

import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.dto.ZkBioAttendanceDTO;
import tn.iteam.dto.ZkBioProblemDTO;

import java.util.List;

/**
 * Interface for ZKBio business operations.
 * Contains only business methods.
 * Monitoring orchestration belongs to ZkBioIntegrationService.
 */
public interface ZkBioServiceInterface {

    ServiceStatusDTO getServerStatus();

    List<ServiceStatusDTO> fetchDevices();

    List<ZkBioProblemDTO> fetchProblems();

    List<ZkBioAttendanceDTO> fetchAttendanceLogs();

    List<ZkBioAttendanceDTO> fetchAttendanceLogs(long startTime, long endTime);

    List<ZkBioAttendanceDTO> fetchUsers();
}