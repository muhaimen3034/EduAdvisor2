package com.example.eduadvisor2.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_activities")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class StudentActivity {

    @Id
    private String studentId;

    // ── Professional ──────────────────────────────────────────────────────────
    private String  linkedinUrl;
    private Integer linkedinScore;       // 0-100: profile completeness / activity level

    // ── Campus Life ───────────────────────────────────────────────────────────
    private Integer clubScore;           // 0-100
    private String  clubDescription;

    private Integer volunteeringScore;   // 0-100
    private String  volunteeringDescription;

    // ── Academic-adjacent ─────────────────────────────────────────────────────
    private Integer skillsScore;         // 0-100: technical skills (coding, tools, etc.)
    private String  skillsDescription;

    private Integer researchScore;       // 0-100: research / project involvement
    private String  researchDescription;

    private Integer leadershipScore;     // 0-100: leadership roles held
    private String  leadershipDescription;

    private LocalDateTime updatedAt;
}
