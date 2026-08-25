package com.example.eduadvisor2.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exam_papers")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ExamPaper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String teacherId;
    private String teacherName;
    private String studentId;
    private String studentName;
    private String studentEmail;

    private String title;
    private String type; // "Assignment" or "Mid-Term"

    @Builder.Default
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "paper_id")
    private List<ExamPaperImage> images = new ArrayList<>();

    private LocalDateTime submittedAt;
}
