package com.atomquest.service;

import com.atomquest.model.*;
import com.atomquest.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class CheckinService {

    private final QuarterlyCheckinRepository checkinRepository;
    private final GoalRepository goalRepository;
    private final AuditLogRepository auditLogRepository;

    /**
     * Employee logs their quarterly achievement
     */
    public QuarterlyCheckin updateCheckin(Long goalId,
                                          QuarterlyCheckin.Quarter quarter,
                                          int year,
                                          Double actualAchievement,
                                          LocalDate completionDate,
                                          QuarterlyCheckin.CheckinStatus status,
                                          String employeeComment,
                                          User user) {
        Goal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new IllegalArgumentException("Goal not found."));

        if (!goal.getStatus().equals(Goal.GoalStatus.APPROVED)) {
            throw new IllegalStateException("Goal must be approved before logging achievement.");
        }

        QuarterlyCheckin checkin = checkinRepository
            .findByGoalIdAndQuarterAndCycleYear(goalId, quarter, year)
            .orElse(QuarterlyCheckin.builder()
                .goal(goal)
                .quarter(quarter)
                .cycleYear(year)
                .build());

        checkin.setActualAchievement(actualAchievement);
        checkin.setCompletionDate(completionDate);
        checkin.setStatus(status);
        checkin.setEmployeeComment(employeeComment);
        checkin.setEmployeeUpdatedAt(LocalDateTime.now());

        // Compute progress score
        double score = computeProgressScore(goal, actualAchievement, completionDate);
        checkin.setProgressScore(score);

        QuarterlyCheckin saved = checkinRepository.save(checkin);

        // Audit
        AuditLog log = AuditLog.builder()
            .performedBy(user)
            .goal(goal)
            .action("CHECKIN_UPDATED")
            .newValue(String.format("Q=%s, Achievement=%s, Score=%.1f%%",
                quarter, actualAchievement, score * 100))
            .description(quarter + " checkin updated by " + user.getName())
            .build();
        auditLogRepository.save(log);

        return saved;
    }

    /**
     * Manager adds structured check-in comment
     */
    public QuarterlyCheckin addManagerComment(Long checkinId, String managerComment, User manager) {
        QuarterlyCheckin checkin = checkinRepository.findById(checkinId)
            .orElseThrow(() -> new IllegalArgumentException("Checkin not found."));

        checkin.setManagerComment(managerComment);
        checkin.setManagerUpdatedAt(LocalDateTime.now());

        QuarterlyCheckin saved = checkinRepository.save(checkin);

        AuditLog log = AuditLog.builder()
            .performedBy(manager)
            .goal(checkin.getGoal())
            .action("MANAGER_COMMENT_ADDED")
            .newValue(managerComment)
            .description("Manager comment added by " + manager.getName())
            .build();
        auditLogRepository.save(log);

        return saved;
    }

    /**
     * Compute progress score based on UoM type
     * Returns value between 0.0 and 1.0 (multiply by 100 for %)
     */
    public double computeProgressScore(Goal goal, Double actualAchievement, LocalDate completionDate) {
        if (actualAchievement == null && completionDate == null) return 0.0;

        return switch (goal.getUomType()) {
            case NUMERIC, PERCENTAGE -> {
                if (goal.getUomDirection() == Goal.UomDirection.MIN) {
                    // Higher is better: Achievement / Target
                    if (goal.getTarget() == null || goal.getTarget() == 0) yield 0.0;
                    yield Math.min(actualAchievement / goal.getTarget(), 1.5); // cap at 150%
                } else {
                    // Lower is better (MAX type): Target / Achievement
                    if (actualAchievement == null || actualAchievement == 0) yield 0.0;
                    yield Math.min(goal.getTarget() / actualAchievement, 1.5);
                }
            }
            case TIMELINE -> {
                // Date-based: Completed on or before deadline = 100%
                if (completionDate == null || goal.getTargetDate() == null) yield 0.0;
                if (!completionDate.isAfter(goal.getTargetDate())) yield 1.0;
                // Penalty for late completion
                long daysLate = goal.getTargetDate().until(completionDate).getDays();
                yield Math.max(0.0, 1.0 - (daysLate * 0.1));
            }
            case ZERO_BASED -> {
                // Zero = 100% success, anything else = 0%
                if (actualAchievement != null && actualAchievement == 0) yield 1.0;
                yield 0.0;
            }
        };
    }

    public List<QuarterlyCheckin> getCheckinsByGoal(Long goalId) {
        return checkinRepository.findByGoalId(goalId);
    }

    public Optional<QuarterlyCheckin> getCheckin(Long goalId, QuarterlyCheckin.Quarter quarter, int year) {
        return checkinRepository.findByGoalIdAndQuarterAndCycleYear(goalId, quarter, year);
    }

    public List<QuarterlyCheckin> getTeamCheckins(Long managerId, QuarterlyCheckin.Quarter quarter, int year) {
        return checkinRepository.findByManagerIdAndQuarterAndYear(managerId, quarter, year);
    }
}
