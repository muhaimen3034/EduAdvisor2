package com.example.eduadvisor2.controller;

import com.example.eduadvisor2.model.AssessmentScore;
import com.example.eduadvisor2.model.Course;
import com.example.eduadvisor2.repository.AssessmentScoreRepository;
import com.example.eduadvisor2.repository.CourseRepository;
import com.example.eduadvisor2.repository.EnrollmentRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/gaps")
@RequiredArgsConstructor
public class LearningGapController {

    private final AssessmentScoreRepository scoreRepository;
    private final EnrollmentRepository      enrollmentRepository;
    private final CourseRepository          courseRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${groq.api.key:}")
    private String apiKey;

    private static final String GROQ_URL    = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL  = "llama-3.1-8b-instant";
    private static final double GAP_THRESHOLD = 65.0;

    // ── Student: save a score ───────────────────────────────────────────────
    @PostMapping("/scores")
    public ResponseEntity<?> saveScore(@RequestBody Map<String, Object> body, HttpSession session) {
        String studentId = (String) session.getAttribute("studentId");
        if (studentId == null) return ResponseEntity.status(401).build();

        AssessmentScore score = AssessmentScore.builder()
                .studentId(studentId)
                .courseCode((String) body.get("courseCode"))
                .courseName((String) body.get("courseName"))
                .assessmentType((String) body.get("assessmentType"))
                .score(((Number) body.get("score")).doubleValue())
                .maxScore(((Number) body.get("maxScore")).doubleValue())
                .notes((String) body.getOrDefault("notes", ""))
                .recordedAt(LocalDateTime.now())
                .build();

        scoreRepository.save(score);
        return ResponseEntity.ok(Map.of("success", true, "id", score.getId()));
    }

    // ── Student: list all scores ────────────────────────────────────────────
    @GetMapping("/scores")
    public List<AssessmentScore> getScores(HttpSession session) {
        String studentId = (String) session.getAttribute("studentId");
        if (studentId == null) return List.of();
        return scoreRepository.findByStudentIdOrderByRecordedAtDesc(studentId);
    }

    // ── Student: delete a score ─────────────────────────────────────────────
    @DeleteMapping("/scores/{id}")
    public ResponseEntity<?> deleteScore(@PathVariable Long id, HttpSession session) {
        if (session.getAttribute("studentId") == null) return ResponseEntity.status(401).build();
        scoreRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── Student: generate AI learning plan ─────────────────────────────────
    @PostMapping("/plan")
    public Map<String, String> generatePlan(HttpSession session) {
        String studentId   = (String) session.getAttribute("studentId");
        String studentName = (String) session.getAttribute("studentName");
        if (studentId == null) return Map.of("plan", "Please log in.");
        if (apiKey == null || apiKey.isBlank()) return Map.of("plan", "AI is not configured. Contact the administrator.");

        List<AssessmentScore> scores = scoreRepository.findByStudentIdOrderByRecordedAtDesc(studentId);
        if (scores.isEmpty())
            return Map.of("plan", "No scores logged yet. Please add at least one assessment score first.");

        List<AssessmentScore> gaps = scores.stream()
                .filter(s -> pct(s) < GAP_THRESHOLD)
                .collect(Collectors.toList());

        StringBuilder ctx = new StringBuilder();
        ctx.append("Student: ").append(studentName).append("\n\n");
        ctx.append("All Logged Assessment Scores:\n");
        for (AssessmentScore s : scores) {
            String creditTag = courseRepository.findById(s.getCourseCode())
                .map(c -> "CLOSED".equals(c.getCreditType()) ? " [CLOSED CREDIT]"
                        : "EXTRA".equals(c.getCreditType())  ? " [EXTRA CREDIT]" : "")
                .orElse("");
            ctx.append(String.format("  - [%s] %s | %s: %.1f/%.1f (%.1f%%) %s%s\n",
                    s.getCourseCode(), s.getCourseName(), s.getAssessmentType(),
                    s.getScore(), s.getMaxScore(), pct(s),
                    pct(s) < 50 ? "[CRITICAL GAP]" : pct(s) < GAP_THRESHOLD ? "[WARNING GAP]" : "[OK]",
                    creditTag));
        }

        ctx.append("\nLearning Gaps (below 65%):\n");
        if (gaps.isEmpty()) {
            ctx.append("  None — all scores above 65%.\n");
        } else {
            for (AssessmentScore g : gaps) {
                ctx.append(String.format("  - %s | %s: %.1f%% (needs +%.1f%% to reach 65%%)\n",
                        g.getCourseName(), g.getAssessmentType(), pct(g), GAP_THRESHOLD - pct(g)));
            }
        }

        String system =
                "You are a caring academic advisor at Daffodil International University (DIU). " +
                "A student has shared their assessment scores and is asking for a personalized learning plan. " +
                "Based on their data, create a clear, structured, and encouraging plan.\n\n" +
                "IMPORTANT: Some courses are marked [CLOSED CREDIT] — these are mandatory batch courses " +
                "that carry the most academic weight. Prioritise improving performance in CLOSED CREDIT " +
                "courses above all others. EXTRA credit courses are optional enrichment.\n\n" +
                "Use exactly these section headers (with emojis):\n" +
                "📌 PRIORITY AREAS\n" +
                "📚 STUDY STRATEGIES\n" +
                "📅 4-WEEK IMPROVEMENT PLAN\n" +
                "🎯 MEASURABLE GOALS\n" +
                "💡 QUICK TIPS\n\n" +
                "Keep it concise, practical, and motivating. Use bullet points. Max 500 words.";

        try {
            List<Map<String, String>> msgs = List.of(
                    Map.of("role", "system", "content", system),
                    Map.of("role", "user",   "content", ctx.toString())
            );
            Map<String, Object> reqBody = new LinkedHashMap<>();
            reqBody.put("model",       GROQ_MODEL);
            reqBody.put("messages",    msgs);
            reqBody.put("max_tokens",  1200);
            reqBody.put("temperature", 0.7);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    GROQ_URL, HttpMethod.POST, new HttpEntity<>(reqBody, headers),
                    (Class<Map<String, Object>>) (Class<?>) Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.getBody().get("choices");
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            return Map.of("plan", (String) msg.get("content"));

        } catch (Exception e) {
            log.error("Learning plan generation error", e);
            return Map.of("plan", "Error generating plan: " + e.getMessage());
        }
    }

    // ── Teacher: get students with learning gaps ────────────────────────────
    @GetMapping("/teacher/students")
    public List<Map<String, Object>> getTeacherGapStudents(HttpSession session) {
        String teacherName = (String) session.getAttribute("teacherName");
        if (teacherName == null) return List.of();

        List<Course> courses = courseRepository.findByInstructor(teacherName);

        Set<String> studentIds = new LinkedHashSet<>();
        for (Course c : courses) {
            enrollmentRepository.findByCourse_Code(c.getCode())
                    .forEach(e -> studentIds.add(e.getStudentId()));
        }
        if (studentIds.isEmpty()) return List.of();

        List<AssessmentScore> allScores = scoreRepository.findByStudentIdIn(new ArrayList<>(studentIds));
        if (allScores.isEmpty()) return List.of();

        Map<String, List<AssessmentScore>> byStudent = allScores.stream()
                .collect(Collectors.groupingBy(AssessmentScore::getStudentId));

        List<Map<String, Object>> results = new ArrayList<>();
        for (Map.Entry<String, List<AssessmentScore>> entry : byStudent.entrySet()) {
            List<AssessmentScore> studentScores = entry.getValue();
            double avgPct = studentScores.stream().mapToDouble(this::pct).average().orElse(0);

            if (avgPct < GAP_THRESHOLD) {
                List<Map<String, Object>> weakDetails = studentScores.stream()
                        .filter(s -> pct(s) < GAP_THRESHOLD)
                        .map(s -> {
                            Map<String, Object> d = new LinkedHashMap<>();
                            d.put("course",     s.getCourseName());
                            d.put("type",       s.getAssessmentType());
                            d.put("pct",        Math.round(pct(s) * 10.0) / 10.0);
                            d.put("critical",   pct(s) < 50);
                            return d;
                        })
                        .collect(Collectors.toList());

                Map<String, Object> m = new LinkedHashMap<>();
                m.put("studentId",   entry.getKey());
                m.put("avgPct",      Math.round(avgPct * 10.0) / 10.0);
                m.put("scoreCount",  studentScores.size());
                m.put("gapCount",    weakDetails.size());
                m.put("weakDetails", weakDetails);
                results.add(m);
            }
        }

        results.sort(Comparator.comparingDouble(m -> (Double) m.get("avgPct")));
        return results;
    }

    private double pct(AssessmentScore s) {
        return s.getMaxScore() == 0 ? 0 : (s.getScore() / s.getMaxScore()) * 100.0;
    }
}
