package com.atomquest.repository;

import com.atomquest.model.QuarterlyCheckin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuarterlyCheckinRepository extends JpaRepository<QuarterlyCheckin, Long> {
    List<QuarterlyCheckin> findByGoalId(Long goalId);

    Optional<QuarterlyCheckin> findByGoalIdAndQuarterAndCycleYear(
        Long goalId, QuarterlyCheckin.Quarter quarter, int cycleYear);

    @Query("SELECT qc FROM QuarterlyCheckin qc WHERE qc.goal.user.id = :userId AND qc.quarter = :quarter AND qc.cycleYear = :year")
    List<QuarterlyCheckin> findByUserIdAndQuarterAndYear(
        @Param("userId") Long userId,
        @Param("quarter") QuarterlyCheckin.Quarter quarter,
        @Param("year") int year);

    @Query("SELECT qc FROM QuarterlyCheckin qc WHERE qc.goal.user.manager.id = :managerId AND qc.quarter = :quarter AND qc.cycleYear = :year")
    List<QuarterlyCheckin> findByManagerIdAndQuarterAndYear(
        @Param("managerId") Long managerId,
        @Param("quarter") QuarterlyCheckin.Quarter quarter,
        @Param("year") int year);

    @Query("SELECT COUNT(DISTINCT qc.goal.user.id) FROM QuarterlyCheckin qc WHERE qc.quarter = :quarter AND qc.cycleYear = :year AND qc.status = 'COMPLETED'")
    Long countCompletedUsersByQuarterAndYear(@Param("quarter") QuarterlyCheckin.Quarter quarter, @Param("year") int year);
}
