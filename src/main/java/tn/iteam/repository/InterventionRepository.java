package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.domain.Intervention;

public interface InterventionRepository extends JpaRepository<Intervention, Long> {
}
