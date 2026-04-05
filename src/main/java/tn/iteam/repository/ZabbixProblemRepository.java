package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.iteam.domain.ZabbixProblem;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZabbixProblemRepository extends JpaRepository<ZabbixProblem, Long> {

    Optional<ZabbixProblem> findByProblemId(String problemId);
    List<ZabbixProblem> findByActiveTrue();
}
