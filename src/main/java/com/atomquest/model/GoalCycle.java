package com.atomquest.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;

@Entity
@Table(name = "goal_cycles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoalCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String cycleName; // e.g. "FY 2025-26"

    @Column(nullable = false)
    private int cycleYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CyclePhase phase;

    @Column(nullable = false)
    private LocalDate windowOpen;

    @Column(nullable = false)
    private LocalDate windowClose;

    @Column(nullable = false)
    private boolean active = false;

    @Column
    private String description;

    public enum CyclePhase {
        GOAL_SETTING,
        Q1_CHECKIN,
        Q2_CHECKIN,
        Q3_CHECKIN,
        Q4_ANNUAL
    }

    public boolean isCurrentlyOpen() {
        LocalDate today = LocalDate.now();
        return active && !today.isBefore(windowOpen) && !today.isAfter(windowClose);
    }
}
