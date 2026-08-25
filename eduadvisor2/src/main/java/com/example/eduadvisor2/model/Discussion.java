package com.example.eduadvisor2.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "discussions")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Discussion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String studentId;
    private String studentName;
    private String studentEmail;   // notification email at time of asking
    private String teacherId;
    private String teacherName;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String question;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String questionImageBase64;

    private String questionImageType;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String answer;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String answerImageBase64;

    private String answerImageType;

    private LocalDateTime askedAt;
    private LocalDateTime answeredAt;
}
