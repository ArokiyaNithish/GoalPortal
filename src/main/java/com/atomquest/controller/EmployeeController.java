package com.atomquest.controller;

import com.atomquest.model.*;
import com.atomquest.repository.*;
import com.atomquest.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/employee")
@RequiredArgsConstructor
public class EmployeeController {

    private final UserRepository userRepository;
    private final GoalService goalService;
    private final CheckinService checkinService;
    private final GoalCycleRepository cycleRepository;
    private final QuarterlyCheckinRepository checkinRepository;

    private User getCurrentUser(Principal principal) {
        return userRepository.findByEmail(principal.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // ====== DASHBOARD ======
    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        User user = getCurrentUser(principal);
        List<Goal> goals = goalService.getGoalsByUser(user);

        long approved = goals.stream().filter(g -> g.getStatus() == Goal.GoalStatus.APPROVED).count();
        long pending = goals.stream().filter(g -> g.getStatus() == Goal.GoalStatus.SUBMITTED).count();
        long draft = goals.stream().filter(g -> g.getStatus() == Goal.GoalStatus.DRAFT ||
                                                g.getStatus() == Goal.GoalStatus.REWORK).count();

        double totalWeightage = goals.stream().mapToDouble(Goal::getWeightage).sum();

        model.addAttribute("user", user);
        model.addAttribute("goals", goals);
        model.addAttribute("approvedCount", approved);
        model.addAttribute("pendingCount", pending);
        model.addAttribute("draftCount", draft);
        model.addAttribute("totalWeightage", totalWeightage);
        model.addAttribute("activeCycle", cycleRepository.findByActiveTrue().orElse(null));

        return "employee/dashboard";
    }

    // ====== GOAL LIST ======
    @GetMapping("/goals")
    public String goalList(Model model, Principal principal) {
        User user = getCurrentUser(principal);
        List<Goal> goals = goalService.getGoalsByUser(user);
        double totalWeightage = goals.stream().mapToDouble(Goal::getWeightage).sum();

        model.addAttribute("user", user);
        model.addAttribute("goals", goals);
        model.addAttribute("totalWeightage", totalWeightage);
        model.addAttribute("canAddMore", goals.size() < GoalService.MAX_GOALS);
        model.addAttribute("activeCycle", cycleRepository.findByActiveTrue().orElse(null));

        return "employee/goal-list";
    }

    // ====== CREATE GOAL ======
    @GetMapping("/goals/create")
    public String createGoalForm(Model model, Principal principal) {
        User user = getCurrentUser(principal);
        List<Goal> existing = goalService.getGoalsByUser(user);

        if (existing.size() >= GoalService.MAX_GOALS) {
            return "redirect:/employee/goals?error=max";
        }

        model.addAttribute("user", user);
        model.addAttribute("goal", new Goal());
        model.addAttribute("thrustAreas", getThrustAreas());
        model.addAttribute("uomTypes", Goal.UomType.values());
        model.addAttribute("remainingWeightage",
            100.0 - existing.stream().mapToDouble(Goal::getWeightage).sum());

        return "employee/goal-create";
    }

    @PostMapping("/goals/create")
    public String createGoal(@ModelAttribute Goal goal,
                              @RequestParam(required = false) String targetDateStr,
                              Principal principal,
                              RedirectAttributes ra) {
        User user = getCurrentUser(principal);
        try {
            if (targetDateStr != null && !targetDateStr.isBlank()) {
                goal.setTargetDate(LocalDate.parse(targetDateStr));
            }
            goalService.createGoal(goal, user);
            ra.addFlashAttribute("success", "Goal created successfully!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/employee/goals/create";
        }
        return "redirect:/employee/goals";
    }

    // ====== EDIT GOAL ======
    @GetMapping("/goals/edit/{id}")
    public String editGoalForm(@PathVariable Long id, Model model, Principal principal) {
        User user = getCurrentUser(principal);
        Goal goal = goalService.findById(id)
            .orElseThrow(() -> new RuntimeException("Goal not found"));

        if (!goal.getUser().getId().equals(user.getId())) {
            return "redirect:/employee/goals?error=unauthorized";
        }

        model.addAttribute("user", user);
        model.addAttribute("goal", goal);
        model.addAttribute("thrustAreas", getThrustAreas());
        model.addAttribute("uomTypes", Goal.UomType.values());

        return "employee/goal-edit";
    }

    @PostMapping("/goals/edit/{id}")
    public String editGoal(@PathVariable Long id, @ModelAttribute Goal updated,
                            @RequestParam(required = false) String targetDateStr,
                            Principal principal, RedirectAttributes ra) {
        User user = getCurrentUser(principal);
        try {
            if (targetDateStr != null && !targetDateStr.isBlank()) {
                updated.setTargetDate(LocalDate.parse(targetDateStr));
            }
            goalService.updateGoal(id, updated, user);
            ra.addFlashAttribute("success", "Goal updated successfully!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/employee/goals";
    }

    // ====== DELETE GOAL ======
    @PostMapping("/goals/delete/{id}")
    public String deleteGoal(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        User user = getCurrentUser(principal);
        try {
            goalService.deleteGoal(id, user);
            ra.addFlashAttribute("success", "Goal deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/employee/goals";
    }

    // ====== SUBMIT ALL GOALS ======
    @PostMapping("/goals/submit-all")
    public String submitAllGoals(Principal principal, RedirectAttributes ra) {
        User user = getCurrentUser(principal);
        try {
            goalService.submitAllGoals(user);
            ra.addFlashAttribute("success", "All goals submitted for manager approval!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/employee/goals";
    }

    // ====== CHECK-IN ======
    @GetMapping("/checkin")
    public String checkinPage(Model model, Principal principal) {
        User user = getCurrentUser(principal);
        List<Goal> goals = goalService.getGoalsByUser(user).stream()
            .filter(g -> g.getStatus() == Goal.GoalStatus.APPROVED)
            .toList();

        int currentYear = LocalDate.now().getYear();
        QuarterlyCheckin.Quarter currentQ = getCurrentQuarter();

        List<Map<String, Object>> goalCheckins = new ArrayList<>();
        for (Goal g : goals) {
            Map<String, Object> item = new HashMap<>();
            item.put("goal", g);
            item.put("checkin", checkinRepository
                .findByGoalIdAndQuarterAndCycleYear(g.getId(), currentQ, currentYear)
                .orElse(null));
            goalCheckins.add(item);
        }

        model.addAttribute("user", user);
        model.addAttribute("goalCheckins", goalCheckins);
        model.addAttribute("currentQuarter", currentQ);
        model.addAttribute("currentYear", currentYear);
        model.addAttribute("quarters", QuarterlyCheckin.Quarter.values());

        return "employee/checkin";
    }

    @PostMapping("/checkin/update")
    public String updateCheckin(@RequestParam Long goalId,
                                 @RequestParam String quarter,
                                 @RequestParam int year,
                                 @RequestParam(required = false) Double actualAchievement,
                                 @RequestParam(required = false) String completionDateStr,
                                 @RequestParam String status,
                                 @RequestParam(required = false) String employeeComment,
                                 Principal principal,
                                 RedirectAttributes ra) {
        User user = getCurrentUser(principal);
        try {
            LocalDate completionDate = completionDateStr != null && !completionDateStr.isBlank()
                ? LocalDate.parse(completionDateStr) : null;

            checkinService.updateCheckin(goalId,
                QuarterlyCheckin.Quarter.valueOf(quarter),
                year, actualAchievement, completionDate,
                QuarterlyCheckin.CheckinStatus.valueOf(status),
                employeeComment, user);

            ra.addFlashAttribute("success", "Achievement updated successfully!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/employee/checkin";
    }

    // ====== GOAL DETAIL ======
    @GetMapping("/goals/view/{id}")
    public String viewGoal(@PathVariable Long id, Model model, Principal principal) {
        User user = getCurrentUser(principal);
        Goal goal = goalService.findById(id)
            .orElseThrow(() -> new RuntimeException("Goal not found"));

        model.addAttribute("user", user);
        model.addAttribute("goal", goal);
        model.addAttribute("checkins", checkinService.getCheckinsByGoal(id));

        return "employee/goal-view";
    }

    // ====== HELPERS ======
    private QuarterlyCheckin.Quarter getCurrentQuarter() {
        int month = LocalDate.now().getMonthValue();
        if (month >= 7 && month <= 9) return QuarterlyCheckin.Quarter.Q1;
        if (month >= 10 && month <= 12) return QuarterlyCheckin.Quarter.Q2;
        if (month >= 1 && month <= 3) return QuarterlyCheckin.Quarter.Q3;
        return QuarterlyCheckin.Quarter.Q4;
    }

    private List<String> getThrustAreas() {
        return List.of(
            "Revenue & Growth",
            "Quality & Excellence",
            "Safety & Compliance",
            "Learning & Development",
            "Operational Efficiency",
            "Customer Satisfaction",
            "Innovation & Technology",
            "People & Culture"
        );
    }
}
