package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.domain.ObserviumProblem;

import java.util.Collection;
import java.util.List;

public interface ObserviumProblemRepository extends JpaRepository<ObserviumProblem, Long> {
    List<ObserviumProblem> findAllByProblemId(String problemId);
    List<ObserviumProblem> findByProblemIdIn(Collection<String> problemIds);
    List<ObserviumProblem> findByActiveTrue();
    List<ObserviumProblem> findBySourceAndActiveTrue(String source);
}
