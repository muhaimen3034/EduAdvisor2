package com.example.eduadvisor2.repository;

import com.example.eduadvisor2.model.Discussion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DiscussionRepository extends JpaRepository<Discussion, Long> {
    List<Discussion> findByStudentIdOrderByAskedAtDesc(String studentId);
    List<Discussion> findByTeacherIdOrderByAskedAtDesc(String teacherId);
}
