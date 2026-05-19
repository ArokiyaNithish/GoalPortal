package com.atomquest.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "quarterly_checkins", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"goal_id", "quarter", "cycle_year"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuarterlyCheckin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id", nullable = false)
    private Goal goal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Quarter quarter;

    @Column(nullable = false)
    private int cycleYear;

    @Column
    private Double actualAchievement;

    @Column
    private LocalDate completionDate; // For Timeline UoM

    @Enumerated(EnumType.STRING)
    @Column
    private CheckinStatus status;

    @Column
    private Double progressScore; // Computed, stored for history

    @Column(columnDefinition = "TEXT")
    private String employeeComment;

    @Column(columnDefinition = "TEXT")
    private String managerComment;

    @Column
    private LocalDateTime employeeUpdatedAt;

    @Column
    private LocalDateTime managerUpdatedAt;

    @PrePersist
    public void prePersist() {
        if (this.status == null) this.status = CheckinStatus.NOT_STARTED;
    }

    public enum Quarter {
        Q1, Q2, Q3, Q4
    }

    public enum CheckinStatus {
        NOT_STARTED, ON_TRACK, COMPLETED
    }
}
