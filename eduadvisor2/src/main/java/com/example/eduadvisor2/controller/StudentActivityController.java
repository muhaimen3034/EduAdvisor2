package com.example.eduadvisor2.controller;

import com.example.eduadvisor2.model.AssessmentScore;
import com.example.eduadvisor2.model.StudentProfileLinks;
import com.example.eduadvisor2.repository.AssessmentScoreRepository;
import com.example.eduadvisor2.repository.StudentProfileLinksRepository;
import com.example.eduadvisor2.service.StudentService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
public class StudentActivityController {

    private final AssessmentScoreRepository      scoreRepository;
    private final StudentProfileLinksRepository  profileRepo;
    private final StudentService                 studentService;

    // ── Student: own summary profile (used by dashboard radar chart) ──────
    @GetMapping
    public Map<String, Object> getOwn(HttpSession session) {
        String sid = (String) session.getAttribute("studentId");
        if (sid == null) return Map.of();
        return buildProfile(sid);
    }

    // ── Advisor / Coordinator: any student's profile ──────────────────────
    @GetMapping("/{studentId}")
    public Map<String, Object> getForAdvisor(@PathVariable String studentId, HttpSession session) {
        boolean isCoord     = session.getAttribute("coordinatorId") != null;
        String advisorBatch = (String) session.getAttribute("advisorBatch");
        if (!isCoord && (advisorBatch == null || !studentId.startsWith(advisorBatch + "-")))
            return Map.of("error", "Not authorized");
        return buildProfile(studentId);
    }

    // ── Shared builder ────────────────────────────────────────────────────
    private Map<String, Object> buildProfile(String sid) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Axis 1: Academic CG (GPA → 0-100)
        double gpa = studentService.findByStudentId(sid)
            .map(s -> s.getGpa() != null ? s.getGpa() : 0.0).orElse(0.0);
        double academicScore = Math.min((gpa / 4.0) * 100.0, 100.0);
        result.put("academicScore", round1(academicScore));
        result.put("gpa", gpa);

        // Axis 2: Course Performance (assessment average)
        List<AssessmentScore> scores = scoreRepository.findByStudentIdOrderByRecordedAtDesc(sid);
        double coursePerf = scores.stream()
            .filter(s -> s.getMaxScore() > 0)
            .mapToDouble(s -> (s.getScore() / s.getMaxScore()) * 100.0)
            .average().orElse(0.0);
        result.put("coursePerf", round1(coursePerf));

        // Axes 3-7: AI-analysed profile scores (from StudentProfileLinks)
        StudentProfileLinks p = profileRepo.findById(sid).orElse(null);
        boolean hasProfile = p != null && "DONE".equals(p.getAnalysisStatus());

        double linkedinScore  = hasProfile ? orZ(p.getLinkedinScore())   : 0;
        double projectsScore  = hasProfile ? orZ(p.getProjectsScore())  : 0;
        double researchScore  = hasProfile ? orZ(p.getResearchScore())  : 0;
        double githubScore    = hasProfile ? orZ(p.getGithubScore())    : 0;
        double technicalScore = hasProfile ? orZ(p.getTechnicalScore()) : 0;

        result.put("linkedinScore",  linkedinScore);
        result.put("networkScore",   hasProfile ? orZ(p.getNetworkScore())  : 0);
        result.put("contentScore",   hasProfile ? orZ(p.getContentScore())  : 0);
        result.put("projectsScore",  projectsScore);
        result.put("seminarsScore",  hasProfile ? orZ(p.getSeminarsScore()) : 0);
        result.put("bootcampsScore", hasProfile ? orZ(p.getBootcampsScore()): 0);
        result.put("researchScore",  researchScore);
        result.put("githubScore",    githubScore);
        result.put("technicalScore", technicalScore);
        result.put("profileSetup",   hasProfile);
        result.put("linkedinUrl",    p != null ? nvl(p.getLinkedinUrl()) : "");
        result.put("githubUrl",      p != null ? nvl(p.getGithubUrl())   : "");

        // Radar axes (7) — dashboard summary view
        double[] axisScores = {
            academicScore, coursePerf,
            linkedinScore, projectsScore, researchScore,
            githubScore,   technicalScore
        };
        String[] axisLabels = {
            "Academic CG", "Course Perf.",
            "LinkedIn", "Projects", "Research",
            "GitHub", "Technical"
        };

        long gaps = 0, strengths = 0, averages = 0;
        for (double s : axisScores) {
            if      (s >= 70) strengths++;
            else if (s >= 40) averages++;
            else              gaps++;
        }
        result.put("gapCount",      gaps);
        result.put("strengthCount", strengths);
        result.put("avgCount",      averages);
        result.put("axisScores",    axisScores);
        result.put("axisLabels",    axisLabels);

        return result;
    }

    private double round1(double v)  { return Math.round(v * 10.0) / 10.0; }
    private double orZ(Integer v)    { return v != null ? v : 0; }
    private String nvl(String v)     { return v != null ? v : ""; }
}
