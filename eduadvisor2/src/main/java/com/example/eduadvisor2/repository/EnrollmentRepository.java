package com.example.eduadvisor2.repository;

import com.example.eduadvisor2.model.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByStudentId(String studentId);
    boolean existsByStudentIdAndCourse_Code(String studentId, String courseCode);
    long countByCourse_Code(String courseCode);
    List<Enrollment> findByCourse_Code(String courseCode);
}
