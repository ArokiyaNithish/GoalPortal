package com.atomquest.repository;

import com.atomquest.model.Goal;
import com.atomquest.model.GoalCycle;
import com.atomquest.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GoalRepository extends JpaRepository<Goal, Long> {
    List<Goal> findByUser(User user);
    List<Goal> findByUserAndCycle(User user, GoalCycle cycle);
    List<Goal> findByUserIdAndCycleId(Long userId, Long cycleId);
    List<Goal> findByStatus(Goal.GoalStatus status);

    @Query("SELECT g FROM Goal g WHERE g.user.manager.id = :managerId AND g.cycle.id = :cycleId")
    List<Goal> findByManagerIdAndCycleId(@Param("managerId") Long managerId, @Param("cycleId") Long cycleId);

    @Query("SELECT SUM(g.weightage) FROM Goal g WHERE g.user.id = :userId AND g.cycle.id = :cycleId AND g.status != 'DRAFT'")
    Double sumWeightageByUserAndCycle(@Param("userId") Long userId, @Param("cycleId") Long cycleId);

    @Query("SELECT COUNT(g) FROM Goal g WHERE g.user.id = :userId AND g.cycle.id = :cycleId")
    Long countByUserAndCycle(@Param("userId") Long userId, @Param("cycleId") Long cycleId);

    List<Goal> findBySharedFromGoalId(Long sharedFromGoalId);

    @Query("SELECT g FROM Goal g WHERE g.user.manager.id = :managerId")
    List<Goal> findAllByManagerId(@Param("managerId") Long managerId);
}
