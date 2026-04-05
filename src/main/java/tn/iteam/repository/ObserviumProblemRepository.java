package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.domain.ObserviumProblem;

public interface ObserviumProblemRepository extends JpaRepository<ObserviumProblem, Long> {
    // tu peux ajouter des méthodes custom si nécessaire
}