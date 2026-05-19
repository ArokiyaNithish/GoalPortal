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
public class GoalService {

    private final GoalRepository goalRepository;
    private final GoalCycleRepository cycleRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public static final int MAX_GOALS = 8;
    public static final double MIN_WEIGHTAGE = 10.0;
    public static final double TOTAL_WEIGHTAGE = 100.0;

    // ====== GOAL CREATION ======

    public Goal createGoal(Goal goal, User user) {
        GoalCycle activeCycle = cycleRepository.findByActiveTrue()
            .orElseThrow(() -> new IllegalStateException("No active goal setting cycle found."));

        long existingCount = goalRepository.countByUserAndCycle(user.getId(), activeCycle.getId());
        if (existingCount >= MAX_GOALS) {
            throw new IllegalStateException("Maximum " + MAX_GOALS + " goals allowed per employee.");
        }
        if (goal.getWeightage() < MIN_WEIGHTAGE) {
            throw new IllegalArgumentException("Minimum weightage per goal is " + MIN_WEIGHTAGE + "%.");
        }

        goal.setUser(user);
        goal.setCycle(activeCycle);
        goal.setStatus(Goal.GoalStatus.DRAFT);
        goal.setCreatedAt(LocalDateTime.now());
        goal.setUpdatedAt(LocalDateTime.now());

        Goal saved = goalRepository.save(goal);
        auditLog(user, saved, "GOAL_CREATED", null, "Goal created: " + goal.getTitle());
        return saved;
    }

    public Goal updateGoal(Long goalId, Goal updated, User user) {
        Goal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new IllegalArgumentException("Goal not found."));

        if (goal.isLocked() && !user.getRole().equals(User.Role.ADMIN)) {
            throw new IllegalStateException("Goal is locked. Contact Admin to unlock.");
        }
        if (!goal.getStatus().equals(Goal.GoalStatus.DRAFT) &&
            !goal.getStatus().equals(Goal.GoalStatus.REWORK)) {
            throw new IllegalStateException("Goal cannot be edited in its current state.");
        }

        String old = goalToString(goal);
        goal.setThrustArea(updated.getThrustArea());
        goal.setTitle(updated.getTitle());
        goal.setDescription(updated.getDescription());
        goal.setUomType(updated.getUomType());
        goal.setUomDirection(updated.getUomDirection());
        goal.setTarget(updated.getTarget());
        goal.setTargetDate(updated.getTargetDate());
        goal.setWeightage(updated.getWeightage());
        goal.setUpdatedAt(LocalDateTime.now());

        Goal saved = goalRepository.save(goal);
        auditLog(user, saved, "GOAL_UPDATED", old, goalToString(saved));
        return saved;
    }

    public void deleteGoal(Long goalId, User user) {
        Goal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new IllegalArgumentException("Goal not found."));
        if (!goal.getStatus().equals(Goal.GoalStatus.DRAFT)) {
            throw new IllegalStateException("Only draft goals can be deleted.");
        }
        auditLog(user, goal, "GOAL_DELETED", goalToString(goal), null);
        goalRepository.delete(goal);
    }

    // ====== SUBMIT FOR APPROVAL ======

    public Goal submitGoal(Long goalId, User user) {
        Goal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new IllegalArgumentException("Goal not found."));

        // Validate total weightage
        validateTotalWeightage(user, goal.getCycle(), goal);

        goal.setStatus(Goal.GoalStatus.SUBMITTED);
        goal.setSubmittedAt(LocalDateTime.now());
        goal.setUpdatedAt(LocalDateTime.now());
        Goal saved = goalRepository.save(goal);
        auditLog(user, saved, "GOAL_SUBMITTED", null, "Goal submitted for approval");
        return saved;
    }

    public void submitAllGoals(User user) {
        GoalCycle activeCycle = cycleRepository.findByActiveTrue()
            .orElseThrow(() -> new IllegalStateException("No active cycle."));
        List<Goal> goals = goalRepository.findByUserAndCycle(user, activeCycle);

        double totalWeightage = goals.stream()
            .mapToDouble(Goal::getWeightage).sum();
        if (Math.abs(totalWeightage - TOTAL_WEIGHTAGE) > 0.01) {
            throw new IllegalStateException(
                "Total weightage must be exactly 100%. Current total: " + totalWeightage + "%");
        }
        if (goals.isEmpty()) {
            throw new IllegalStateException("No goals to submit.");
        }

        goals.forEach(g -> {
            if (g.getStatus() == Goal.GoalStatus.DRAFT || g.getStatus() == Goal.GoalStatus.REWORK) {
                g.setStatus(Goal.GoalStatus.SUBMITTED);
                g.setSubmittedAt(LocalDateTime.now());
                goalRepository.save(g);
                auditLog(user, g, "GOAL_SUBMITTED", null, "Bulk submitted");
            }
        });
    }

    // ====== MANAGER APPROVAL ======

    public Goal approveGoal(Long goalId, User manager) {
        Goal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new IllegalArgumentException("Goal not found."));

        goal.setStatus(Goal.GoalStatus.APPROVED);
        goal.setApprovedAt(LocalDateTime.now());
        goal.setLockedAt(LocalDateTime.now());
        goal.setUpdatedAt(LocalDateTime.now());
        Goal saved = goalRepository.save(goal);
        auditLog(manager, saved, "GOAL_APPROVED", null, "Goal approved and locked by manager");
        
        if (saved.getUser() != null && saved.getUser().getEmail() != null) {
            emailService.sendGoalUpdateEmail(saved.getUser().getEmail(), "AtomQuest - Goal Approved", 
                "Good news! Your goal '" + saved.getTitle() + "' has been approved by your manager.");
        }
        
        return saved;
    }

    public Goal returnForRework(Long goalId, User manager, String remark) {
        Goal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new IllegalArgumentException("Goal not found."));

        goal.setStatus(Goal.GoalStatus.REWORK);
        goal.setLockedAt(null);
        goal.setManagerRemark(remark);
        goal.setUpdatedAt(LocalDateTime.now());
        Goal saved = goalRepository.save(goal);
        auditLog(manager, saved, "GOAL_RETURNED", null, "Returned for rework: " + remark);
        
        if (saved.getUser() != null && saved.getUser().getEmail() != null) {
            emailService.sendGoalUpdateEmail(saved.getUser().getEmail(), "AtomQuest - Goal Requires Rework", 
                "Your goal '" + saved.getTitle() + "' has been returned by your manager for rework.\nRemark: " + remark);
        }
        
        return saved;
    }

    public Goal managerEditAndApprove(Long goalId, Double newTarget, Double newWeightage,
                                      String remark, User manager) {
        Goal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new IllegalArgumentException("Goal not found."));

        String old = goalToString(goal);
        if (newTarget != null) goal.setTarget(newTarget);
        if (newWeightage != null) goal.setWeightage(newWeightage);
        if (remark != null) goal.setManagerRemark(remark);
        goal.setStatus(Goal.GoalStatus.APPROVED);
        goal.setApprovedAt(LocalDateTime.now());
        goal.setLockedAt(LocalDateTime.now());
        goal.setUpdatedAt(LocalDateTime.now());

        Goal saved = goalRepository.save(goal);
        auditLog(manager, saved, "GOAL_MANAGER_EDITED_AND_APPROVED", old, goalToString(saved));
        return saved;
    }

    // ====== ADMIN UNLOCK ======

    public Goal unlockGoal(Long goalId, User admin) {
        Goal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new IllegalArgumentException("Goal not found."));

        goal.setLockedAt(null);
        goal.setStatus(Goal.GoalStatus.REWORK);
        goal.setUpdatedAt(LocalDateTime.now());
        Goal saved = goalRepository.save(goal);
        auditLog(admin, saved, "GOAL_UNLOCKED", null, "Unlocked by admin");
        return saved;
    }

    // ====== SHARED GOALS ======

    public void pushSharedGoal(Long sourceGoalId, List<Long> targetUserIds, User pushedBy) {
        Goal source = goalRepository.findById(sourceGoalId)
            .orElseThrow(() -> new IllegalArgumentException("Source goal not found."));

        GoalCycle activeCycle = cycleRepository.findByActiveTrue()
            .orElseThrow(() -> new IllegalStateException("No active cycle."));

        for (Long userId : targetUserIds) {
            User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Target user not found: " + userId));

            Goal shared = Goal.builder()
                .user(targetUser)
                .thrustArea(source.getThrustArea())
                .title(source.getTitle())
                .description(source.getDescription())
                .uomType(source.getUomType())
                .uomDirection(source.getUomDirection())
                .target(source.getTarget())
                .targetDate(source.getTargetDate())
                .weightage(10.0) // Default, recipient can change
                .status(Goal.GoalStatus.DRAFT)
                .shared(true)
                .sharedFromGoalId(source.getId())
                .weightageEditable(true)
                .cycle(activeCycle)
                .build();

            goalRepository.save(shared);
            auditLog(pushedBy, shared, "SHARED_GOAL_PUSHED",
                null, "Shared goal pushed to: " + targetUser.getName());
        }
    }

    // ====== QUERIES ======

    public List<Goal> getGoalsByUser(User user) {
        GoalCycle activeCycle = cycleRepository.findByActiveTrue().orElse(null);
        if (activeCycle == null) return List.of();
        return goalRepository.findByUserAndCycle(user, activeCycle);
    }

    public List<Goal> getTeamGoals(User manager) {
        GoalCycle activeCycle = cycleRepository.findByActiveTrue().orElse(null);
        if (activeCycle == null) return List.of();
        return goalRepository.findByManagerIdAndCycleId(manager.getId(), activeCycle.getId());
    }

    public Optional<Goal> findById(Long id) {
        return goalRepository.findById(id);
    }

    public List<Goal> getAllGoals() {
        return goalRepository.findAll();
    }

    // ====== HELPERS ======

    private void validateTotalWeightage(User user, GoalCycle cycle, Goal currentGoal) {
        List<Goal> goals = goalRepository.findByUserAndCycle(user, cycle);
        double total = goals.stream()
            .filter(g -> !g.getId().equals(currentGoal.getId()))
            .mapToDouble(Goal::getWeightage)
            .sum() + currentGoal.getWeightage();

        if (Math.abs(total - TOTAL_WEIGHTAGE) > 0.01) {
            throw new IllegalStateException(
                "Total weightage must equal 100%. Current: " + total + "%");
        }
    }

    private void auditLog(User user, Goal goal, String action, String old, String newVal) {
        AuditLog log = AuditLog.builder()
            .performedBy(user)
            .goal(goal)
            .action(action)
            .oldValue(old)
            .newValue(newVal)
            .description(action + " by " + user.getName())
            .build();
        auditLogRepository.save(log);
    }

    private String goalToString(Goal g) {
        return String.format("Title=%s, Target=%s, Weightage=%s, Status=%s",
            g.getTitle(), g.getTarget(), g.getWeightage(), g.getStatus());
    }
}
