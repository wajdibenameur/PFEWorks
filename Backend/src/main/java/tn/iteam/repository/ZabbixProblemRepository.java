package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tn.iteam.domain.ZabbixProblem;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ZabbixProblemRepository extends JpaRepository<ZabbixProblem, Long> {

    List<ZabbixProblem> findAllByProblemId(String problemId);
    List<ZabbixProblem> findByActiveTrue();
    long countByActiveTrue();

    @Query("select p from ZabbixProblem p where p.hostId in :hostIds and p.startedAt >= :start order by p.hostId asc, p.startedAt asc")
    List<ZabbixProblem> findRecentProblemsForHosts(Collection<Long> hostIds, Long start);
}
