package com.example.eduadvisor2.repository;

import com.example.eduadvisor2.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByStudentId(String studentId);
    List<Student> findByStudentIdStartingWith(String prefix);
}
