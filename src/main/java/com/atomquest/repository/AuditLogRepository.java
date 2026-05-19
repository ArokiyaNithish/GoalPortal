package com.atomquest.repository;

import com.atomquest.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByGoalIdOrderByTimestampDesc(Long goalId);
    List<AuditLog> findAllByOrderByTimestampDesc();
    List<AuditLog> findByPerformedByIdOrderByTimestampDesc(Long userId);
}
