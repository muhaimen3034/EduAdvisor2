package com.example.eduadvisor2.repository;

import com.example.eduadvisor2.model.FeeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeeStatusRepository extends JpaRepository<FeeStatus, Long> {
    Optional<FeeStatus> findByStudentId(String studentId);
    List<FeeStatus> findAllByOrderBySubmittedAtDesc();
    List<FeeStatus> findByStatusOrderBySubmittedAtDesc(String status);
}
