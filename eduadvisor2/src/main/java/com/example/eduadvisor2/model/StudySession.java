package com.example.eduadvisor2.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "study_sessions")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class StudySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String studentId;
    private String courseCode;
    private String courseName;
    private String topicHint;
    private int difficulty;      // 1 = Easy, 2 = Medium, 3 = Hard
    private int score;           // correct answers
    private int total;           // total questions
    private LocalDateTime createdAt;
}
