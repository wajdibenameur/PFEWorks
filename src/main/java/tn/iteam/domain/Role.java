package tn.iteam.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tn.iteam.Enums.Permission;
import tn.iteam.Enums.RoleName;

import java.util.List;

@Entity
@Getter
@Setter
public class Role extends BaseEntity {

    @Enumerated(EnumType.STRING)
    private RoleName name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission", nullable = false)
    @Enumerated(EnumType.STRING)
    private List<Permission> permissions;
}
