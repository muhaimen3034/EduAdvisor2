package com.example.eduadvisor2.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "assessment_scores")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AssessmentScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String studentId;
    private String courseCode;
    private String courseName;
    private String assessmentType; // QUIZ, MID, ASSIGNMENT, FINAL, PRESENTATION, ATTENDANCE

    private double score;
    private double maxScore;

    @Column(length = 500)
    private String notes;

    private LocalDateTime recordedAt;
}
