package com.example.eduadvisor2.controller;

import com.example.eduadvisor2.model.StudentProfileLinks;
import com.example.eduadvisor2.repository.StudentProfileLinksRepository;
import com.example.eduadvisor2.service.ProfileAnalysisService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileAnalysisController {

    private final StudentProfileLinksRepository profileRepo;
    private final ProfileAnalysisService analysisService;

    // ── Student: get own analysis ─────────────────────────────────────────
    @GetMapping
    public Map<String, Object> getOwn(HttpSession session) {
        String sid = (String) session.getAttribute("studentId");
        if (sid == null) return Map.of("error", "Not logged in");
        return toMap(profileRepo.findById(sid).orElse(null));
    }

    // ── Student: submit / update URLs and trigger analysis ────────────────
    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestBody Map<String, String> body, HttpSession session) {
        String sid = (String) session.getAttribute("studentId");
        if (sid == null) return ResponseEntity.status(401).build();

        String linkedin = body.getOrDefault("linkedinUrl", "").trim();
        String github   = body.getOrDefault("githubUrl",   "").trim();
        String actDesc  = body.getOrDefault("activityDescription", "").trim();
        if (linkedin.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "LinkedIn URL is required"));

        StudentProfileLinks result = analysisService.analyzeAndSave(sid, linkedin, github.isEmpty() ? null : github, actDesc.isEmpty() ? null : actDesc);
        return ResponseEntity.ok(toMap(result));
    }

    // ── Student: re-run analysis with existing URLs ───────────────────────
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpSession session) {
        String sid = (String) session.getAttribute("studentId");
        if (sid == null) return ResponseEntity.status(401).build();

        Optional<StudentProfileLinks> opt = profileRepo.findById(sid);
        if (opt.isEmpty() || opt.get().getLinkedinUrl() == null)
            return ResponseEntity.badRequest().body(Map.of("error", "No profile linked yet"));

        StudentProfileLinks p = opt.get();
        StudentProfileLinks result = analysisService.analyzeAndSave(sid, p.getLinkedinUrl(), p.getGithubUrl(), p.getActivityDescription());
        return ResponseEntity.ok(toMap(result));
    }

    // ── Student: update URLs / activity description then re-analyze ───────
    @PatchMapping("/urls")
    public ResponseEntity<?> updateUrls(@RequestBody Map<String, String> body, HttpSession session) {
        String sid = (String) session.getAttribute("studentId");
        if (sid == null) return ResponseEntity.status(401).build();

        String linkedin = body.getOrDefault("linkedinUrl", "").trim();
        String github   = body.getOrDefault("githubUrl",   "").trim();
        String actDesc  = body.getOrDefault("activityDescription", "").trim();
        if (linkedin.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "LinkedIn URL is required"));

        StudentProfileLinks result = analysisService.analyzeAndSave(sid, linkedin, github.isEmpty() ? null : github, actDesc.isEmpty() ? null : actDesc);
        return ResponseEntity.ok(toMap(result));
    }

    // ── Advisor / Coordinator: view any student's profile ────────────────
    @GetMapping("/{studentId}")
    public Map<String, Object> getForAdvisor(@PathVariable String studentId, HttpSession session) {
        boolean isCoord    = session.getAttribute("coordinatorId") != null;
        String advisorBatch = (String) session.getAttribute("advisorBatch");

        if (!isCoord && (advisorBatch == null || !studentId.startsWith(advisorBatch + "-")))
            return Map.of("error", "Not authorized");

        return toMap(profileRepo.findById(studentId).orElse(null));
    }

    // ── Serialiser ────────────────────────────────────────────────────────
    private Map<String, Object> toMap(StudentProfileLinks p) {
        if (p == null) return Map.of("status", "NOT_SETUP");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status",          p.getAnalysisStatus() != null ? p.getAnalysisStatus() : "NOT_SETUP");
        m.put("linkedinUrl",     nvl(p.getLinkedinUrl()));
        m.put("githubUrl",       nvl(p.getGithubUrl()));
        m.put("linkedinScore",   orZ(p.getLinkedinScore()));
        m.put("networkScore",    orZ(p.getNetworkScore()));
        m.put("contentScore",    orZ(p.getContentScore()));
        m.put("projectsScore",   orZ(p.getProjectsScore()));
        m.put("seminarsScore",   orZ(p.getSeminarsScore()));
        m.put("bootcampsScore",  orZ(p.getBootcampsScore()));
        m.put("researchScore",   orZ(p.getResearchScore()));
        m.put("githubScore",     orZ(p.getGithubScore()));
        m.put("technicalScore",  orZ(p.getTechnicalScore()));
        m.put("linkedinSummary", nvl(p.getLinkedinSummary()));
        m.put("githubSummary",   nvl(p.getGithubSummary()));
        m.put("strengthsJson",   nvl(p.getStrengthsJson()));
        m.put("gapsJson",        nvl(p.getGapsJson()));
        m.put("recommendationsJson", nvl(p.getRecommendationsJson()));
        m.put("githubRepos",     orZ(p.getGithubRepos()));
        m.put("githubStars",     orZ(p.getGithubStars()));
        m.put("githubFollowers", orZ(p.getGithubFollowers()));
        m.put("githubLanguages", nvl(p.getGithubLanguages()));
        m.put("activityDescription", nvl(p.getActivityDescription()));
        m.put("lastRefreshedAt", p.getLastRefreshedAt() != null ? p.getLastRefreshedAt().toString() : "");
        return m;
    }

    private int    orZ(Integer v) { return v != null ? v : 0; }
    private String nvl(String v)  { return v != null ? v : ""; }
}
