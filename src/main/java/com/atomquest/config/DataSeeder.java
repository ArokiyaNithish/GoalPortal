package com.atomquest.config;

import com.atomquest.model.*;
import com.atomquest.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataSeeder {

    private final UserRepository userRepository;
    private final GoalCycleRepository cycleRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner seedData() {
        return args -> {
            log.info("Seeding initial data...");

            // ====== Seed Users ======
            if (!userRepository.existsByEmail("admin@atomquest.com")) {

                // Admin
                User admin = userRepository.save(User.builder()
                    .name("Admin User")
                    .email("admin@atomquest.com")
                    .password(passwordEncoder.encode("admin123"))
                    .role(User.Role.ADMIN)
                    .department("HR")
                    .designation("HR Administrator")
                    .active(true)
                    .build());

                // Manager
                User manager = userRepository.save(User.builder()
                    .name("Rajesh Kumar")
                    .email("manager@atomquest.com")
                    .password(passwordEncoder.encode("mgr123"))
                    .role(User.Role.MANAGER)
                    .department("Engineering")
                    .designation("Engineering Manager")
                    .active(true)
                    .build());

                // Employee 1
                User emp1 = userRepository.save(User.builder()
                    .name("Priya Sharma")
                    .email("employee@atomquest.com")
                    .password(passwordEncoder.encode("emp123"))
                    .role(User.Role.EMPLOYEE)
                    .department("Engineering")
                    .designation("Software Engineer")
                    .manager(manager)
                    .active(true)
                    .build());

                // Employee 2
                User emp2 = userRepository.save(User.builder()
                    .name("Arjun Patel")
                    .email("arjun@atomquest.com")
                    .password(passwordEncoder.encode("emp123"))
                    .role(User.Role.EMPLOYEE)
                    .department("Engineering")
                    .designation("Senior Developer")
                    .manager(manager)
                    .active(true)
                    .build());

                // Employee 3
                User emp3 = userRepository.save(User.builder()
                    .name("Sneha Reddy")
                    .email("sneha@atomquest.com")
                    .password(passwordEncoder.encode("emp123"))
                    .role(User.Role.EMPLOYEE)
                    .department("Sales")
                    .designation("Sales Executive")
                    .manager(manager)
                    .active(true)
                    .build());

                // Demo User
                User demoUser = userRepository.save(User.builder()
                    .name("Demo User")
                    .email("demo@atomquest.com")
                    .password(passwordEncoder.encode("demo123"))
                    .role(User.Role.EMPLOYEE)
                    .department("Product")
                    .designation("Product Manager")
                    .manager(manager)
                    .active(true)
                    .build());

                log.info("Users seeded: Admin, Manager, 3 Employees, 1 Demo User");

                // ====== Seed Goal Cycle ======
                GoalCycle cycle = cycleRepository.save(GoalCycle.builder()
                    .cycleName("FY 2025-26")
                    .cycleYear(2025)
                    .phase(GoalCycle.CyclePhase.GOAL_SETTING)
                    .windowOpen(LocalDate.of(2025, 5, 1))
                    .windowClose(LocalDate.of(2026, 4, 30))
                    .active(true)
                    .description("Annual goal cycle for FY 2025-26")
                    .build());

                log.info("Goal Cycle seeded: FY 2025-26 (Active)");

                // ====== Seed Sample Goals for emp1 ======
                seedSampleGoals(emp1, cycle);
                log.info("Sample goals seeded for employee: {}", emp1.getName());
                
                // ====== Seed Sample Goals for Demo User ======
                seedSampleGoals(demoUser, cycle);
                log.info("Sample goals seeded for demo user: {}", demoUser.getName());
            }

            log.info("Data seeding complete.");
            log.info("===========================================");
            log.info("Demo Credentials:");
            log.info("  Admin    -> admin@atomquest.com / admin123");
            log.info("  Manager  -> manager@atomquest.com / mgr123");
            log.info("  Employee -> employee@atomquest.com / emp123");
            log.info("  Demo     -> demo@atomquest.com / demo123");
            log.info("===========================================");
        };
    }

    private void seedSampleGoals(User emp, GoalCycle cycle) {
        // Goal 1 - Numeric/Min (higher is better)
        Goal g1 = Goal.builder()
            .user(emp)
            .thrustArea("Revenue & Growth")
            .title("Increase Feature Delivery Velocity")
            .description("Deliver 20 story points per sprint on average")
            .uomType(Goal.UomType.NUMERIC)
            .uomDirection(Goal.UomDirection.MIN)
            .target(20.0)
            .weightage(30.0)
            .status(Goal.GoalStatus.APPROVED)
            .shared(false)
            .cycle(cycle)
            .build();

        // Goal 2 - Percentage/Min
        Goal g2 = Goal.builder()
            .user(emp)
            .thrustArea("Quality & Excellence")
            .title("Improve Code Quality Score")
            .description("Achieve minimum 85% code coverage in unit tests")
            .uomType(Goal.UomType.PERCENTAGE)
            .uomDirection(Goal.UomDirection.MIN)
            .target(85.0)
            .weightage(25.0)
            .status(Goal.GoalStatus.APPROVED)
            .shared(false)
            .cycle(cycle)
            .build();

        // Goal 3 - Zero-based (safety incidents)
        Goal g3 = Goal.builder()
            .user(emp)
            .thrustArea("Safety & Compliance")
            .title("Zero Security Incidents")
            .description("Maintain zero P0 security incidents in production")
            .uomType(Goal.UomType.ZERO_BASED)
            .target(0.0)
            .weightage(20.0)
            .status(Goal.GoalStatus.APPROVED)
            .shared(false)
            .cycle(cycle)
            .build();

        // Goal 4 - Timeline
        Goal g4 = Goal.builder()
            .user(emp)
            .thrustArea("Learning & Development")
            .title("Complete AWS Certification")
            .description("Obtain AWS Solutions Architect Associate certification")
            .uomType(Goal.UomType.TIMELINE)
            .targetDate(LocalDate.of(2025, 12, 31))
            .weightage(15.0)
            .status(Goal.GoalStatus.SUBMITTED)
            .shared(false)
            .cycle(cycle)
            .build();

        // Goal 5 - Numeric/Max (lower TAT is better)
        Goal g5 = Goal.builder()
            .user(emp)
            .thrustArea("Operational Efficiency")
            .title("Reduce Bug Resolution TAT")
            .description("Reduce average bug resolution time to under 2 days")
            .uomType(Goal.UomType.NUMERIC)
            .uomDirection(Goal.UomDirection.MAX)
            .target(2.0)
            .weightage(10.0)
            .status(Goal.GoalStatus.DRAFT)
            .shared(false)
            .cycle(cycle)
            .build();
    }
}
