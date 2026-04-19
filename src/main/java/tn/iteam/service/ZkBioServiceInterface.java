package tn.iteam.service;

import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.dto.ZkBioAttendanceDTO;
import tn.iteam.dto.ZkBioProblemDTO;

import java.util.List;

/**
 * Interface for ZKBio business operations.
 * This interface contains only business methods (status, devices, attendance, users).
 * For unified monitoring operations, use ZkBioMonitoringService instead.
 */
public interface ZkBioServiceInterface {

    /**
     * Gets the ZKBio server status.
     * @return server status DTO
     */
    ServiceStatusDTO getServerStatus();

    /**
     * Fetches all devices from ZKBio.
     * @return list of service status DTOs
     */
    List<ServiceStatusDTO> fetchDevices();

    /**
     * Fetches all problems/alerts from ZKBio.
     * @return list of problem DTOs
     */
    List<ZkBioProblemDTO> fetchProblems();

    /**
     * Fetches all attendance logs from ZKBio.
     * @return list of attendance DTOs
     */
    List<ZkBioAttendanceDTO> fetchAttendanceLogs();

    /**
     * Fetches attendance logs within a time range.
     * @param startTime start timestamp
     * @param endTime end timestamp
     * @return list of attendance DTOs
     */
    List<ZkBioAttendanceDTO> fetchAttendanceLogs(long startTime, long endTime);

    /**
     * Fetches all users from ZKBio.
     * @return list of attendance DTOs (user data)
     */
    List<ZkBioAttendanceDTO> fetchUsers();

    /**
     * Triggers manual data collection.
     */
    void collectData();
}
