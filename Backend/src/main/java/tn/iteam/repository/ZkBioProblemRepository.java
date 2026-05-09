package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.domain.ZkBioProblem;

import java.util.List;

public interface ZkBioProblemRepository extends JpaRepository<ZkBioProblem, Long> {
    List<ZkBioProblem> findAllByProblemId(String problemId);
    List<ZkBioProblem> findByActiveTrue();
}
