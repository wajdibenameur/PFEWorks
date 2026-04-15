package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.iteam.domain.ZabbixProblem;

import java.util.List;

@Repository
public interface ZabbixProblemRepository extends JpaRepository<ZabbixProblem, Long> {

    List<ZabbixProblem> findAllByProblemId(String problemId);
    List<ZabbixProblem> findByActiveTrue();
}