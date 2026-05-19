package com.atomquest.repository;

import com.atomquest.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    List<User> findByManagerId(Long managerId);
    List<User> findByRole(User.Role role);
    List<User> findByRoleAndActive(User.Role role, boolean active);
    boolean existsByEmail(String email);
}
