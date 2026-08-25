package com.example.eduadvisor2.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "exam_paper_images")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ExamPaperImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String imageBase64;

    private String imageType;
}
