package com.example.eduadvisor2.repository;

import com.example.eduadvisor2.model.AssessmentScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AssessmentScoreRepository extends JpaRepository<AssessmentScore, Long> {
    List<AssessmentScore> findByStudentIdOrderByRecordedAtDesc(String studentId);
    List<AssessmentScore> findByStudentIdIn(List<String> studentIds);

    @Transactional
    void deleteByStudentId(String studentId);
}
