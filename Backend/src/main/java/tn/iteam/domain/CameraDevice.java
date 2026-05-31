package tn.iteam.domain;

import jakarta.persistence.*;
import lombok.*;
import tn.iteam.enums.DeviceStatus;

import java.time.Instant;

@Entity
@Table(name = "camera_list")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class CameraDevice extends BaseEntity {

    @Column(name = "ip_address", unique = true, nullable = false, length = 64)
    private String ipAddress;

    @Column(length = 64)
    private String subnet;

    private Integer port;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private DeviceStatus status = DeviceStatus.UNKNOWN;

    private Instant lastSeen;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;
}