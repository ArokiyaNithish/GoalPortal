package com.atomquest.controller;

import com.atomquest.model.*;
import com.atomquest.repository.*;
import com.atomquest.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final GoalService goalService;
    private final GoalCycleRepository cycleRepository;
    private final AuditLogRepository auditLogRepository;
    private final ReportService reportService;
    private final GoalRepository goalRepository;

    private User getCurrentUser(Principal principal) {
        return userRepository.findByEmail(principal.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // ====== ADMIN DASHBOARD ======
    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        User admin = getCurrentUser(principal);
        QuarterlyCheckin.Quarter currentQ = getCurrentQuarter();
        int currentYear = LocalDate.now().getYear();

        Map<String, Object> dashData = reportService.getDashboardData(currentQ, currentYear);

        long totalUsers = userRepository.count();
        long totalGoals = goalRepository.count();
        long totalCycles = cycleRepository.count();

        model.addAttribute("user", admin);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalGoals", totalGoals);
        model.addAttribute("totalCycles", totalCycles);
        model.addAttribute("dashData", dashData);
        model.addAttribute("currentQuarter", currentQ);
        model.addAttribute("activeCycle", cycleRepository.findByActiveTrue().orElse(null));
        model.addAttribute("allCycles", cycleRepository.findAllByOrderByWindowOpenDesc());
        model.addAttribute("recentAuditLogs",
            auditLogRepository.findAllByOrderByTimestampDesc().stream().limit(10).toList());

        return "admin/dashboard";
    }

    // ====== USER MANAGEMENT ======
    @GetMapping("/users")
    public String manageUsers(Model model, Principal principal) {
        model.addAttribute("user", getCurrentUser(principal));
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("managers", userRepository.findByRole(User.Role.MANAGER));
        return "admin/users";
    }

    @PostMapping("/users/create")
    public String createUser(@RequestParam String name,
                              @RequestParam String email,
                              @RequestParam String password,
                              @RequestParam User.Role role,
                              @RequestParam(required = false) String department,
                              @RequestParam(required = false) String designation,
                              @RequestParam(required = false) Long managerId,
                              RedirectAttributes ra) {
        try {
            if (userRepository.existsByEmail(email)) {
                ra.addFlashAttribute("error", "Email already exists.");
                return "redirect:/admin/users";
            }
            User manager = managerId != null ? userRepository.findById(managerId).orElse(null) : null;
            User user = User.builder()
                .name(name).email(email)
                .password(org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.class
                    .getDeclaredConstructor().newInstance().encode(password))
                .role(role).department(department)
                .designation(designation).manager(manager).active(true)
                .build();
            userRepository.save(user);
            ra.addFlashAttribute("success", "User created: " + name);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Failed to create user: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/toggle/{id}")
    public String toggleUser(@PathVariable Long id, RedirectAttributes ra) {
        userRepository.findById(id).ifPresent(u -> {
            u.setActive(!u.isActive());
            userRepository.save(u);
            ra.addFlashAttribute("success", "User status toggled.");
        });
        return "redirect:/admin/users";
    }

    // ====== GOAL CYCLE MANAGEMENT ======
    @GetMapping("/cycles")
    public String manageCycles(Model model, Principal principal) {
        model.addAttribute("user", getCurrentUser(principal));
        model.addAttribute("cycles", cycleRepository.findAllByOrderByWindowOpenDesc());
        model.addAttribute("phases", GoalCycle.CyclePhase.values());
        return "admin/cycles";
    }

    @PostMapping("/cycles/create")
    public String createCycle(@RequestParam String cycleName,
                               @RequestParam int cycleYear,
                               @RequestParam GoalCycle.CyclePhase phase,
                               @RequestParam String windowOpen,
                               @RequestParam String windowClose,
                               @RequestParam(defaultValue = "false") boolean active,
                               @RequestParam(required = false) String description,
                               RedirectAttributes ra) {
        try {
            if (active) {
                // Deactivate all existing active cycles
                cycleRepository.findByActiveTrue().ifPresent(existing -> {
                    existing.setActive(false);
                    cycleRepository.save(existing);
                });
            }
            GoalCycle cycle = GoalCycle.builder()
                .cycleName(cycleName).cycleYear(cycleYear).phase(phase)
                .windowOpen(LocalDate.parse(windowOpen))
                .windowClose(LocalDate.parse(windowClose))
                .active(active).description(description)
                .build();
            cycleRepository.save(cycle);
            ra.addFlashAttribute("success", "Cycle created: " + cycleName);
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/cycles";
    }

    @PostMapping("/cycles/activate/{id}")
    public String activateCycle(@PathVariable Long id, RedirectAttributes ra) {
        cycleRepository.findByActiveTrue().ifPresent(c -> { c.setActive(false); cycleRepository.save(c); });
        cycleRepository.findById(id).ifPresent(c -> { c.setActive(true); cycleRepository.save(c); });
        ra.addFlashAttribute("success", "Cycle activated.");
        return "redirect:/admin/cycles";
    }

    // ====== GOAL MANAGEMENT ======
    @GetMapping("/goals")
    public String allGoals(Model model, Principal principal) {
        model.addAttribute("user", getCurrentUser(principal));
        model.addAttribute("goals", goalService.getAllGoals());
        return "admin/goals";
    }

    @PostMapping("/goals/unlock/{id}")
    public String unlockGoal(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        User admin = getCurrentUser(principal);
        try {
            goalService.unlockGoal(id, admin);
            ra.addFlashAttribute("success", "Goal unlocked successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/goals";
    }

    @PostMapping("/goals/push-shared")
    public String pushSharedGoal(@RequestParam Long sourceGoalId,
                                  @RequestParam List<Long> targetUserIds,
                                  Principal principal, RedirectAttributes ra) {
        User admin = getCurrentUser(principal);
        try {
            goalService.pushSharedGoal(sourceGoalId, targetUserIds, admin);
            ra.addFlashAttribute("success", "Shared goal pushed to " + targetUserIds.size() + " employee(s).");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/goals";
    }

    // ====== AUDIT LOGS ======
    @GetMapping("/audit-log")
    public String auditLog(Model model, Principal principal) {
        model.addAttribute("user", getCurrentUser(principal));
        model.addAttribute("auditLogs", auditLogRepository.findAllByOrderByTimestampDesc());
        return "admin/audit-log";
    }

    // ====== REPORTS ======
    @GetMapping("/reports")
    public String reports(Model model, Principal principal) {
        model.addAttribute("user", getCurrentUser(principal));
        model.addAttribute("quarters", QuarterlyCheckin.Quarter.values());
        model.addAttribute("currentYear", LocalDate.now().getYear());

        QuarterlyCheckin.Quarter currentQ = getCurrentQuarter();
        int currentYear = LocalDate.now().getYear();
        Map<String, Object> dashData = reportService.getDashboardData(currentQ, currentYear);
        model.addAttribute("dashData", dashData);
        model.addAttribute("currentQuarter", currentQ);

        return "admin/reports";
    }

    @GetMapping("/reports/export")
    public ResponseEntity<byte[]> exportReport(@RequestParam String quarter,
                                                @RequestParam int year) {
        try {
            byte[] data = reportService.generateAchievementReport(
                QuarterlyCheckin.Quarter.valueOf(quarter), year);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=achievement_report_" + quarter + "_" + year + ".xlsx")
                .contentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private QuarterlyCheckin.Quarter getCurrentQuarter() {
        int month = LocalDate.now().getMonthValue();
        if (month >= 7 && month <= 9) return QuarterlyCheckin.Quarter.Q1;
        if (month >= 10 && month <= 12) return QuarterlyCheckin.Quarter.Q2;
        if (month >= 1 && month <= 3) return QuarterlyCheckin.Quarter.Q3;
        return QuarterlyCheckin.Quarter.Q4;
    }
}
