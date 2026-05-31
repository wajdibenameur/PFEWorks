package tn.iteam.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tn.iteam.enums.Permission;
import tn.iteam.enums.RoleName;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "role")
public class Role extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, unique = true)
    private RoleName name;

    @ElementCollection(fetch = FetchType.EAGER)

    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission", nullable = false,length = 100)
    @Enumerated(EnumType.STRING)
    private List<Permission> permissions = new ArrayList<>();
}
