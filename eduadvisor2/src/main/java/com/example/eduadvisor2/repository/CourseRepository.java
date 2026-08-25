package com.example.eduadvisor2.repository;

import com.example.eduadvisor2.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, String> {
    List<Course> findByInstructor(String instructor);
}
