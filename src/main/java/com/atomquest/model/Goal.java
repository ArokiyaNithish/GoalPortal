package com.atomquest.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "goals")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String thrustArea;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UomType uomType;

    @Enumerated(EnumType.STRING)
    @Column
    private UomDirection uomDirection; // MIN or MAX (for Numeric/Percentage)

    @Column
    private Double target;

    @Column
    private LocalDate targetDate; // For Timeline UoM

    @Column(nullable = false)
    private Double weightage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GoalStatus status;

    @Column(nullable = false)
    private boolean shared = false;

    @Column
    private Long sharedFromGoalId; // If pushed from a shared goal

    @Column
    private boolean weightageEditable = true; // Only weightage can be edited in shared goals

    @Column
    private LocalDateTime submittedAt;

    @Column
    private LocalDateTime approvedAt;

    @Column
    private LocalDateTime lockedAt;

    @Column(columnDefinition = "TEXT")
    private String managerRemark;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_id")
    private GoalCycle cycle;

    @OneToMany(mappedBy = "goal", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<QuarterlyCheckin> checkins;

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = GoalStatus.DRAFT;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isLocked() {
        return this.lockedAt != null;
    }

    public enum UomType {
        NUMERIC, PERCENTAGE, TIMELINE, ZERO_BASED
    }

    public enum UomDirection {
        MIN, MAX
    }

    public enum GoalStatus {
        DRAFT, SUBMITTED, APPROVED, REWORK, LOCKED
    }
}
