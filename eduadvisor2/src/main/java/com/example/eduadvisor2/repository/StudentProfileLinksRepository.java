package com.example.eduadvisor2.repository;

import com.example.eduadvisor2.model.StudentProfileLinks;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StudentProfileLinksRepository extends JpaRepository<StudentProfileLinks, String> {
    List<StudentProfileLinks> findByAnalysisStatus(String status);
}
