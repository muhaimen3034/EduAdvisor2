package com.example.eduadvisor2.controller;

import com.example.eduadvisor2.model.AssessmentScore;
import com.example.eduadvisor2.model.StudySession;
import com.example.eduadvisor2.repository.AssessmentScoreRepository;
import com.example.eduadvisor2.repository.StudySessionRepository;
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
@RequestMapping("/api/study")
@RequiredArgsConstructor
public class StudyCoachController {

    private final StudySessionRepository  sessionRepository;
    private final AssessmentScoreRepository scoreRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${groq.api.key:}")
    private String apiKey;

    private static final String GROQ_URL   = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.1-8b-instant";

    // ── Generate 5 adaptive MCQ questions ──────────────────────────────────
    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody Map<String, Object> body, HttpSession session) {
        String studentId = (String) session.getAttribute("studentId");
        if (studentId == null) return Map.of("error", "Not logged in");
        if (apiKey == null || apiKey.isBlank()) return Map.of("error", "AI not configured");

        String courseName = (String) body.getOrDefault("courseName", "General Studies");
        String topicHint  = (String) body.getOrDefault("topicHint",  "core concepts");
        int difficulty    = ((Number) body.getOrDefault("difficulty", 2)).intValue();
        String diffLabel  = difficulty == 1 ? "Easy (factual recall, definitions)"
                          : difficulty == 2 ? "Medium (conceptual understanding, comparisons)"
                          :                   "Hard (application, analysis, problem-solving)";

        // Check recent session performance to advise difficulty in prompt
        List<StudySession> recent = sessionRepository
                .findByStudentIdAndCourseCodeOrderByCreatedAtDesc(
                        studentId, (String) body.getOrDefault("courseCode", ""));
        String perfNote = "";
        if (!recent.isEmpty()) {
            double avgPct = recent.stream().limit(3)
                    .mapToDouble(s -> s.getTotal() == 0 ? 0 : (s.getScore() * 100.0 / s.getTotal()))
                    .average().orElse(0);
            perfNote = String.format(
                    " Note: this student recently scored %.0f%% average on this course. ", avgPct);
        }

        String system =
                "You are an academic quiz generator for university students at Daffodil International University.\n" +
                "Generate exactly 5 multiple-choice questions about: \"" + topicHint + "\"\n" +
                "Course: " + courseName + "\n" +
                "Difficulty: " + diffLabel + "\n" + perfNote +
                "\nRules:\n" +
                "- Each question has exactly 4 options.\n" +
                "- 'correct' is the 0-based index (0=A, 1=B, 2=C, 3=D) of the correct answer.\n" +
                "- 'explanation' is 1-2 sentences explaining WHY that answer is correct.\n" +
                "- Make all 4 options plausible (no obviously wrong choices).\n" +
                "- Return ONLY the JSON array. No markdown. No extra text.\n\n" +
                "Format:\n" +
                "[{\"q\":\"Question?\",\"options\":[\"A\",\"B\",\"C\",\"D\"],\"correct\":0,\"explanation\":\"Because...\"}]";

        try {
            List<Map<String, String>> msgs = List.of(
                    Map.of("role", "system", "content", system),
                    Map.of("role", "user",   "content", "Generate the quiz now.")
            );
            Map<String, Object> reqBody = new LinkedHashMap<>();
            reqBody.put("model",       GROQ_MODEL);
            reqBody.put("messages",    msgs);
            reqBody.put("max_tokens",  2000);
            reqBody.put("temperature", 0.8);

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
            String raw = ((String) msg.get("content")).trim();

            // Strip markdown fences
            if (raw.startsWith("```")) {
                raw = raw.replaceAll("```[a-zA-Z]*\\n?", "").replace("```", "").trim();
            }
            // Extract JSON array
            int start = raw.indexOf('[');
            int end   = raw.lastIndexOf(']');
            if (start >= 0 && end > start) raw = raw.substring(start, end + 1);

            return Map.of("questions", raw);

        } catch (Exception e) {
            log.error("Quiz generation error", e);
            return Map.of("error", "Failed to generate quiz: " + e.getMessage());
        }
    }

    // ── Save completed session ──────────────────────────────────────────────
    @PostMapping("/sessions")
    public ResponseEntity<?> saveSession(@RequestBody Map<String, Object> body, HttpSession session) {
        String studentId = (String) session.getAttribute("studentId");
        if (studentId == null) return ResponseEntity.status(401).build();

        StudySession s = StudySession.builder()
                .studentId(studentId)
                .courseCode((String) body.getOrDefault("courseCode", ""))
                .courseName((String) body.getOrDefault("courseName", ""))
                .topicHint((String)  body.getOrDefault("topicHint",  ""))
                .difficulty(((Number) body.getOrDefault("difficulty", 2)).intValue())
                .score(((Number) body.getOrDefault("score", 0)).intValue())
                .total(((Number) body.getOrDefault("total", 5)).intValue())
                .createdAt(LocalDateTime.now())
                .build();

        sessionRepository.save(s);
        return ResponseEntity.ok(Map.of("success", true, "id", s.getId()));
    }

    // ── Get session history ─────────────────────────────────────────────────
    @GetMapping("/sessions")
    public List<StudySession> getSessions(HttpSession session) {
        String studentId = (String) session.getAttribute("studentId");
        if (studentId == null) return List.of();
        return sessionRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
    }

    // ── Smart recommendation: what to study next ────────────────────────────
    @GetMapping("/recommend")
    public Map<String, Object> recommend(HttpSession session) {
        String studentId = (String) session.getAttribute("studentId");
        if (studentId == null) return Map.of();

        // Find the weakest course from assessment scores
        List<AssessmentScore> scores = scoreRepository.findByStudentIdOrderByRecordedAtDesc(studentId);
        Map<String, Object> rec = new LinkedHashMap<>();

        if (!scores.isEmpty()) {
            // Group by course, find lowest avg%
            Map<String, List<AssessmentScore>> byCourse = scores.stream()
                    .collect(Collectors.groupingBy(AssessmentScore::getCourseCode));

            String worstCode = null;
            String worstName = "";
            double worstPct  = Double.MAX_VALUE;

            for (Map.Entry<String, List<AssessmentScore>> e : byCourse.entrySet()) {
                double avg = e.getValue().stream()
                        .mapToDouble(s -> s.getMaxScore() == 0 ? 0 : (s.getScore() / s.getMaxScore()) * 100)
                        .average().orElse(100);
                if (avg < worstPct) {
                    worstPct  = avg;
                    worstCode = e.getKey();
                    worstName = e.getValue().get(0).getCourseName();
                }
            }

            if (worstCode != null) {
                rec.put("courseCode",  worstCode);
                rec.put("courseName",  worstName);
                rec.put("avgPct",      Math.round(worstPct * 10.0) / 10.0);

                // Recommend difficulty based on avg %
                int suggestedDiff = worstPct < 50 ? 1 : worstPct < 70 ? 2 : 3;
                rec.put("difficulty",  suggestedDiff);
                rec.put("reason",      String.format(
                        "Your average in %s is %.1f%%. Practice here first.", worstName, worstPct));
            }
        }

        // Most recent session for this student
        List<StudySession> recent = sessionRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
        if (!recent.isEmpty()) {
            StudySession last = recent.get(0);
            double lastPct = last.getTotal() == 0 ? 0 : (last.getScore() * 100.0 / last.getTotal());
            rec.put("lastScore",     last.getScore() + "/" + last.getTotal());
            rec.put("lastCourseName", last.getCourseName());
            rec.put("lastPct",       Math.round(lastPct * 10.0) / 10.0);
        }

        rec.put("totalSessions", recent.size());
        return rec;
    }
}
