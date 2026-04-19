package tn.iteam.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO pour les journaux de présence ZKBio
 * Représente un événement de pointage (entrée/sortie)
 */
@Data
@Builder
public class ZkBioAttendanceDTO {
    private String userId;
    private String userName;
    private String deviceId;
    private String deviceName;
    private Long timestamp;
    private String verifyType;      // FP, Password, Card, Face, etc.
    private String inOutMode;       // CheckIn, CheckOut
    private String status;          // Normal,异常
    private String eventType;       // Attendance, Access, etc.
    private String source;          // "ZKBIO"
}