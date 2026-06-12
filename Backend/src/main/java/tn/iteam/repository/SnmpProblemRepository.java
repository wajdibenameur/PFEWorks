package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.iteam.domain.SnmpProblem;

import java.util.Collection;
import java.util.List;

public interface SnmpProblemRepository extends JpaRepository<SnmpProblem, Long> {
    List<SnmpProblem> findAllByProblemId(String problemId);
    List<SnmpProblem> findByProblemIdIn(Collection<String> problemIds);
    List<SnmpProblem> findByActiveTrue();
    List<SnmpProblem> findBySourceAndActiveTrue(String source);
    @Query("""
            select p from SnmpProblem p
            where p.active = true
              and coalesce(p.lastObservedAt, p.startedAt, 0) >= :cutoff
            order by coalesce(p.lastObservedAt, p.startedAt, 0) desc, p.startedAt desc, p.id desc
            """)
    List<SnmpProblem> findRecentActiveProblems(@Param("cutoff") long cutoff);
}
