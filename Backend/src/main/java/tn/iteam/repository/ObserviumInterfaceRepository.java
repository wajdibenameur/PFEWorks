package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.domain.ObserviumInterface;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ObserviumInterfaceRepository extends JpaRepository<ObserviumInterface, Long> {
    List<ObserviumInterface> findByHostIdIn(Collection<String> hostIds);
    Optional<ObserviumInterface> findByHostIdAndIfIndex(String hostId, Integer ifIndex);
}
