package com.atomquest.service;

import com.atomquest.model.*;
import com.atomquest.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final GoalRepository goalRepository;
    private final QuarterlyCheckinRepository checkinRepository;
    private final UserRepository userRepository;

    /**
     * Generate Excel report: All employees - Planned vs Actual Achievement
     */
    public byte[] generateAchievementReport(QuarterlyCheckin.Quarter quarter, int year) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Achievement Report");

            // Header styles
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Headers
            Row header = sheet.createRow(0);
            String[] columns = {"Employee Name", "Email", "Department", "Thrust Area",
                "Goal Title", "UoM Type", "Target", "Actual Achievement",
                "Progress Score (%)", "Status", "Manager"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            List<Goal> allGoals = goalRepository.findAll();
            int rowNum = 1;
            for (Goal goal : allGoals) {
                Optional<QuarterlyCheckin> checkin = checkinRepository
                    .findByGoalIdAndQuarterAndCycleYear(goal.getId(), quarter, year);

                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(goal.getUser().getName());
                row.createCell(1).setCellValue(goal.getUser().getEmail());
                row.createCell(2).setCellValue(goal.getUser().getDepartment() != null ?
                    goal.getUser().getDepartment() : "");
                row.createCell(3).setCellValue(goal.getThrustArea());
                row.createCell(4).setCellValue(goal.getTitle());
                row.createCell(5).setCellValue(goal.getUomType().name());
                row.createCell(6).setCellValue(goal.getTarget() != null ? goal.getTarget() : 0);
                row.createCell(7).setCellValue(
                    checkin.map(c -> c.getActualAchievement() != null ? c.getActualAchievement() : 0.0)
                        .orElse(0.0));
                row.createCell(8).setCellValue(
                    checkin.map(c -> c.getProgressScore() != null ? c.getProgressScore() * 100 : 0.0)
                        .orElse(0.0));
                row.createCell(9).setCellValue(
                    checkin.map(c -> c.getStatus().name()).orElse("NOT_STARTED"));
                row.createCell(10).setCellValue(
                    goal.getUser().getManager() != null ?
                        goal.getUser().getManager().getName() : "N/A");
            }

            // Auto-size columns
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Dashboard data: completion rates by department
     */
    public Map<String, Object> getDashboardData(QuarterlyCheckin.Quarter quarter, int year) {
        Map<String, Object> data = new LinkedHashMap<>();

        List<User> allEmployees = userRepository.findByRole(User.Role.EMPLOYEE);
        long total = allEmployees.size();

        long completed = allEmployees.stream().filter(emp -> {
            List<Goal> goals = goalRepository.findByUser(emp);
            if (goals.isEmpty()) return false;
            return goals.stream().allMatch(g ->
                checkinRepository.findByGoalIdAndQuarterAndCycleYear(g.getId(), quarter, year)
                    .map(c -> c.getStatus() == QuarterlyCheckin.CheckinStatus.COMPLETED)
                    .orElse(false));
        }).count();

        data.put("totalEmployees", total);
        data.put("completedCheckins", completed);
        data.put("pendingCheckins", total - completed);
        data.put("completionRate", total > 0 ? (completed * 100.0 / total) : 0.0);

        // By department
        Map<String, Long> byDept = allEmployees.stream()
            .filter(e -> e.getDepartment() != null)
            .collect(Collectors.groupingBy(User::getDepartment, Collectors.counting()));
        data.put("byDepartment", byDept);

        // Goal status distribution
        List<Goal> allGoals = goalRepository.findAll();
        Map<String, Long> statusDist = allGoals.stream()
            .collect(Collectors.groupingBy(g -> g.getStatus().name(), Collectors.counting()));
        data.put("goalStatusDistribution", statusDist);

        return data;
    }

    /**
     * QoQ trend data for charts
     */
    public Map<String, Object> getQoQTrendData(Long userId, int year) {
        Map<String, Object> data = new LinkedHashMap<>();
        List<Goal> goals = goalRepository.findAll().stream()
            .filter(g -> g.getUser().getId().equals(userId))
            .collect(Collectors.toList());

        double[] scores = new double[4];
        QuarterlyCheckin.Quarter[] quarters = QuarterlyCheckin.Quarter.values();
        for (int i = 0; i < quarters.length; i++) {
            final int qi = i;
            double avgScore = goals.stream()
                .mapToDouble(g -> {
                    Optional<QuarterlyCheckin> c = checkinRepository
                        .findByGoalIdAndQuarterAndCycleYear(g.getId(), quarters[qi], year);
                    return c.map(ch -> ch.getProgressScore() != null ? ch.getProgressScore() : 0.0)
                        .orElse(0.0);
                })
                .average().orElse(0.0);
            scores[i] = avgScore * 100;
        }

        data.put("quarters", List.of("Q1", "Q2", "Q3", "Q4"));
        data.put("scores", scores);
        return data;
    }
}
