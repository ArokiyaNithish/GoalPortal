package com.atomquest.controller;

import com.atomquest.model.*;
import com.atomquest.repository.*;
import com.atomquest.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/manager")
@RequiredArgsConstructor
public class ManagerController {

    private final UserRepository userRepository;
    private final GoalService goalService;
    private final CheckinService checkinService;
    private final GoalCycleRepository cycleRepository;
    private final QuarterlyCheckinRepository checkinRepository;
    private final GoalRepository goalRepository;

    private User getCurrentUser(Principal principal) {
        return userRepository.findByEmail(principal.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // ====== DASHBOARD ======
    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        User manager = getCurrentUser(principal);
        List<User> team = userRepository.findByManagerId(manager.getId());
        List<Goal> teamGoals = goalService.getTeamGoals(manager);

        long pendingApproval = teamGoals.stream()
            .filter(g -> g.getStatus() == Goal.GoalStatus.SUBMITTED).count();
        long approvedGoals = teamGoals.stream()
            .filter(g -> g.getStatus() == Goal.GoalStatus.APPROVED).count();

        // Team goal status distribution for chart
        Map<String, Long> statusDist = teamGoals.stream()
            .collect(Collectors.groupingBy(g -> g.getStatus().name(), Collectors.counting()));

        model.addAttribute("user", manager);
        model.addAttribute("team", team);
        model.addAttribute("teamGoals", teamGoals);
        model.addAttribute("pendingApproval", pendingApproval);
        model.addAttribute("approvedGoals", approvedGoals);
        model.addAttribute("teamSize", team.size());
        model.addAttribute("statusDistribution", statusDist);
        model.addAttribute("activeCycle", cycleRepository.findByActiveTrue().orElse(null));

        return "manager/dashboard";
    }

    // ====== TEAM GOALS - PENDING APPROVAL ======
    @GetMapping("/goals/pending")
    public String pendingApprovals(Model model, Principal principal) {
        User manager = getCurrentUser(principal);
        List<Goal> teamGoals = goalService.getTeamGoals(manager);
        List<Goal> pending = teamGoals.stream()
            .filter(g -> g.getStatus() == Goal.GoalStatus.SUBMITTED)
            .collect(Collectors.toList());

        // Group by employee
        Map<User, List<Goal>> byEmployee = pending.stream()
            .collect(Collectors.groupingBy(Goal::getUser));

        model.addAttribute("user", manager);
        model.addAttribute("goalsByEmployee", byEmployee);
        model.addAttribute("totalPending", pending.size());

        return "manager/pending-approvals";
    }

    // ====== APPROVE GOAL ======
    @PostMapping("/goals/approve/{id}")
    public String approveGoal(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        User manager = getCurrentUser(principal);
        try {
            goalService.approveGoal(id, manager);
            ra.addFlashAttribute("success", "Goal approved and locked.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/manager/goals/pending";
    }

    // ====== RETURN FOR REWORK ======
    @PostMapping("/goals/return/{id}")
    public String returnGoal(@PathVariable Long id,
                              @RequestParam String remark,
                              Principal principal, RedirectAttributes ra) {
        User manager = getCurrentUser(principal);
        try {
            goalService.returnForRework(id, manager, remark);
            ra.addFlashAttribute("success", "Goal returned for rework.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/manager/goals/pending";
    }

    // ====== INLINE EDIT & APPROVE ======
    @PostMapping("/goals/edit-approve/{id}")
    public String editAndApprove(@PathVariable Long id,
                                  @RequestParam(required = false) Double newTarget,
                                  @RequestParam(required = false) Double newWeightage,
                                  @RequestParam(required = false) String remark,
                                  Principal principal, RedirectAttributes ra) {
        User manager = getCurrentUser(principal);
        try {
            goalService.managerEditAndApprove(id, newTarget, newWeightage, remark, manager);
            ra.addFlashAttribute("success", "Goal edited and approved.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/manager/goals/pending";
    }

    // ====== TEAM DASHBOARD ======
    @GetMapping("/team")
    public String teamDashboard(Model model, Principal principal) {
        User manager = getCurrentUser(principal);
        List<User> team = userRepository.findByManagerId(manager.getId());

        int currentYear = LocalDate.now().getYear();
        QuarterlyCheckin.Quarter currentQ = getCurrentQuarter();

        List<Map<String, Object>> teamData = new ArrayList<>();
        for (User emp : team) {
            Map<String, Object> empData = new HashMap<>();
            empData.put("employee", emp);
            List<Goal> goals = goalRepository.findByUser(emp);
            empData.put("goals", goals);
            empData.put("goalCount", goals.size());
            empData.put("approvedCount",
                goals.stream().filter(g -> g.getStatus() == Goal.GoalStatus.APPROVED).count());

            // Compute avg progress score for current quarter
            double avgScore = goals.stream()
                .filter(g -> g.getStatus() == Goal.GoalStatus.APPROVED)
                .mapToDouble(g -> checkinRepository
                    .findByGoalIdAndQuarterAndCycleYear(g.getId(), currentQ, currentYear)
                    .map(c -> c.getProgressScore() != null ? c.getProgressScore() * 100 : 0.0)
                    .orElse(0.0))
                .average().orElse(0.0);
            empData.put("avgProgressScore", String.format("%.1f", avgScore));
            teamData.add(empData);
        }

        model.addAttribute("user", manager);
        model.addAttribute("teamData", teamData);
        model.addAttribute("currentQuarter", currentQ);
        model.addAttribute("currentYear", currentYear);

        return "manager/team-dashboard";
    }

    // ====== CHECK-IN VIEW ======
    @GetMapping("/checkin/{employeeId}")
    public String viewEmployeeCheckin(@PathVariable Long employeeId,
                                       Model model, Principal principal) {
        User manager = getCurrentUser(principal);
        User employee = userRepository.findById(employeeId)
            .orElseThrow(() -> new RuntimeException("Employee not found"));

        int currentYear = LocalDate.now().getYear();
        QuarterlyCheckin.Quarter currentQ = getCurrentQuarter();

        List<Goal> goals = goalRepository.findByUser(employee);
        List<Map<String, Object>> goalCheckins = new ArrayList<>();

        for (Goal g : goals) {
            Map<String, Object> item = new HashMap<>();
            item.put("goal", g);
            item.put("checkin", checkinRepository
                .findByGoalIdAndQuarterAndCycleYear(g.getId(), currentQ, currentYear)
                .orElse(null));
            goalCheckins.add(item);
        }

        model.addAttribute("user", manager);
        model.addAttribute("employee", employee);
        model.addAttribute("goalCheckins", goalCheckins);
        model.addAttribute("currentQuarter", currentQ);
        model.addAttribute("currentYear", currentYear);

        return "manager/employee-checkin";
    }

    // ====== ADD MANAGER COMMENT ======
    @PostMapping("/checkin/comment/{checkinId}")
    public String addComment(@PathVariable Long checkinId,
                              @RequestParam String managerComment,
                              @RequestParam Long employeeId,
                              Principal principal, RedirectAttributes ra) {
        User manager = getCurrentUser(principal);
        try {
            checkinService.addManagerComment(checkinId, managerComment, manager);
            ra.addFlashAttribute("success", "Comment added successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/manager/checkin/" + employeeId;
    }

    // ====== PUSH SHARED GOAL ======
    @GetMapping("/shared-goal")
    public String sharedGoalPage(Model model, Principal principal) {
        User manager = getCurrentUser(principal);
        List<User> team = userRepository.findByManagerId(manager.getId());
        List<Goal> approvedGoals = goalService.getTeamGoals(manager).stream()
            .filter(g -> g.getStatus() == Goal.GoalStatus.APPROVED)
            .collect(Collectors.toList());

        model.addAttribute("user", manager);
        model.addAttribute("team", team);
        model.addAttribute("approvedGoals", approvedGoals);

        return "manager/shared-goal";
    }

    @PostMapping("/shared-goal/push")
    public String pushSharedGoal(@RequestParam Long sourceGoalId,
                                  @RequestParam List<Long> targetUserIds,
                                  Principal principal, RedirectAttributes ra) {
        User manager = getCurrentUser(principal);
        try {
            goalService.pushSharedGoal(sourceGoalId, targetUserIds, manager);
            ra.addFlashAttribute("success", "Shared goal pushed to " + targetUserIds.size() + " employee(s).");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/manager/shared-goal";
    }

    private QuarterlyCheckin.Quarter getCurrentQuarter() {
        int month = LocalDate.now().getMonthValue();
        if (month >= 7 && month <= 9) return QuarterlyCheckin.Quarter.Q1;
        if (month >= 10 && month <= 12) return QuarterlyCheckin.Quarter.Q2;
        if (month >= 1 && month <= 3) return QuarterlyCheckin.Quarter.Q3;
        return QuarterlyCheckin.Quarter.Q4;
    }
}
