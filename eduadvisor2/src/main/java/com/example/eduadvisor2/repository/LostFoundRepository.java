package com.example.eduadvisor2.repository;

import com.example.eduadvisor2.model.LostFoundReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LostFoundRepository extends JpaRepository<LostFoundReport, Long> {
    List<LostFoundReport> findByStudentIdOrderByReportedAtDesc(String studentId);
}
