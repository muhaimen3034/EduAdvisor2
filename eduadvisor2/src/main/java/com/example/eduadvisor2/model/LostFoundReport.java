package com.example.eduadvisor2.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "lost_found_reports")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class LostFoundReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String studentId;
    private String studentName;
    private String studentEmail;
    private String location;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String imageBase64;

    private String imageType;

    private LocalDateTime reportedAt;

    private String status;           // PENDING | WAITING | FOUND

    @Lob
    @Column(columnDefinition = "TEXT")
    private String coordinatorNote;

    private LocalDateTime updatedAt;
}
