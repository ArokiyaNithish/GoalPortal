package com.atomquest.repository;

import com.atomquest.model.GoalCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GoalCycleRepository extends JpaRepository<GoalCycle, Long> {
    Optional<GoalCycle> findByActiveTrue();
    List<GoalCycle> findAllByOrderByWindowOpenDesc();
    Optional<GoalCycle> findByPhaseAndCycleYear(GoalCycle.CyclePhase phase, int year);
}
