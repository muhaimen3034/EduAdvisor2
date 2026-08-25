package com.example.eduadvisor2.repository;

import com.example.eduadvisor2.model.StudentActivity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentActivityRepository extends JpaRepository<StudentActivity, String> {
}
