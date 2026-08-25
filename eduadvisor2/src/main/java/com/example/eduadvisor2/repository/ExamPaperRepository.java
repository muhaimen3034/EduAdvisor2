package com.example.eduadvisor2.repository;

import com.example.eduadvisor2.model.ExamPaper;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamPaperRepository extends JpaRepository<ExamPaper, Long> {
    List<ExamPaper> findByStudentIdOrderBySubmittedAtDesc(String studentId);
    List<ExamPaper> findByTeacherIdOrderBySubmittedAtDesc(String teacherId);
}
