package tn.iteam.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tn.iteam.Enums.RoleName;

import java.security.Permission;
import java.util.List;

@Entity
@Getter
@Setter
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private RoleName name;

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    private List<Permission> permissions;
}
