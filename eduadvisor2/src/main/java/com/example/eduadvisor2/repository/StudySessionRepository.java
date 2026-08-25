package com.example.eduadvisor2.repository;

import com.example.eduadvisor2.model.StudySession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {
    List<StudySession> findByStudentIdOrderByCreatedAtDesc(String studentId);
    List<StudySession> findByStudentIdAndCourseCodeOrderByCreatedAtDesc(String studentId, String courseCode);
}
