package com.example.eduadvisor2.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_profile_links")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class StudentProfileLinks {

    @Id
    private String studentId;

    @Column(length = 500)
    private String linkedinUrl;

    @Column(length = 500)
    private String githubUrl;          // optional

    // ── AI / API-computed scores (0-100) ──────────────────────────────────
    private Integer linkedinScore;     // overall LinkedIn profile quality
    private Integer networkScore;      // professional network strength
    private Integer contentScore;      // posts / articles / activity
    private Integer projectsScore;     // LinkedIn projects section quality
    private Integer seminarsScore;     // seminars & workshops attended
    private Integer bootcampsScore;    // bootcamps & intensive training
    private Integer researchScore;     // research publications & academic work
    private Integer githubScore;       // GitHub activity & repo richness
    private Integer technicalScore;    // technical skills from GitHub languages + LinkedIn

    // ── Narrative fields ──────────────────────────────────────────────────
    @Lob @Column(columnDefinition = "TEXT")
    private String linkedinSummary;

    @Lob @Column(columnDefinition = "TEXT")
    private String githubSummary;

    @Lob @Column(columnDefinition = "TEXT")
    private String strengthsJson;       // JSON array of strings

    @Lob @Column(columnDefinition = "TEXT")
    private String gapsJson;            // JSON array of strings

    @Lob @Column(columnDefinition = "TEXT")
    private String recommendationsJson; // JSON array of strings

    // ── GitHub raw stats (for display) ────────────────────────────────────
    private Integer githubRepos;
    private Integer githubStars;
    private Integer githubFollowers;
    private String  githubLanguages;   // comma-separated top languages

    // ── Student self-reported activities (feeds Groq for accurate scoring) ──
    @Lob @Column(columnDefinition = "TEXT")
    private String activityDescription;

    // ── Cached raw LinkedIn profile JSON from Proxycurl ───────────────────
    @Lob @Column(columnDefinition = "TEXT")
    private String linkedinDataJson;    // stored so daily refresh doesn't re-fetch

    // ── Meta ──────────────────────────────────────────────────────────────
    private LocalDateTime lastRefreshedAt;
    private String analysisStatus;     // PENDING | ANALYZING | DONE | ERROR
}
