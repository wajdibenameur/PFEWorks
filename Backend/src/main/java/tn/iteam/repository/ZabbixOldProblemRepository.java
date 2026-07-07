package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.iteam.domain.ZabbixOldProblem;

@Repository
public interface ZabbixOldProblemRepository extends JpaRepository<ZabbixOldProblem, Long> {

    boolean existsByEventId(Long eventId);
}
