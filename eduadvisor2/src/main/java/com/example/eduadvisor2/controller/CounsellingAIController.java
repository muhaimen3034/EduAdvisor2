package com.example.eduadvisor2.controller;

import com.example.eduadvisor2.model.AssessmentScore;
import com.example.eduadvisor2.repository.AssessmentScoreRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/counselling")
@RequiredArgsConstructor
public class CounsellingAIController {

    private final AssessmentScoreRepository scoreRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${groq.api.key:}")
    private String apiKey;

    private static final String GROQ_URL   = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.1-8b-instant";

    // ── Recommend which teacher / dept to book based on weakest subject ────
    @GetMapping("/recommend")
    public Map<String, Object> recommend(HttpSession session) {
        String studentId = (String) session.getAttribute("studentId");
        if (studentId == null) return Map.of();

        List<AssessmentScore> scores = scoreRepository.findByStudentIdOrderByRecordedAtDesc(studentId);
        Map<String, Object> result = new LinkedHashMap<>();

        if (scores.isEmpty()) {
            result.put("hasData",  false);
            result.put("message",  "Log your assessment scores in Learning Gaps to get an AI-powered teacher recommendation.");
            return result;
        }

        Map<String, List<AssessmentScore>> byCourse = scores.stream()
                .collect(Collectors.groupingBy(AssessmentScore::getCourseCode));

        String worstCode = null;
        String worstName = "";
        double worstPct  = Double.MAX_VALUE;
        List<String> weakTypes = new ArrayList<>();

        for (Map.Entry<String, List<AssessmentScore>> e : byCourse.entrySet()) {
            double avg = e.getValue().stream()
                    .mapToDouble(s -> s.getMaxScore() == 0 ? 0 : (s.getScore() / s.getMaxScore()) * 100)
                    .average().orElse(100);
            if (avg < worstPct) {
                worstPct  = avg;
                worstCode = e.getKey();
                worstName = e.getValue().get(0).getCourseName();
                // Collect assessment types where score < 65%
                weakTypes = e.getValue().stream()
                        .filter(s -> s.getMaxScore() > 0 && (s.getScore() / s.getMaxScore() * 100) < 65)
                        .map(AssessmentScore::getAssessmentType)
                        .distinct()
                        .collect(Collectors.toList());
            }
        }

        result.put("hasData",    true);
        result.put("courseCode", worstCode);
        result.put("courseName", worstName);
        result.put("avgPct",     Math.round(worstPct * 10.0) / 10.0);
        result.put("weakTypes",  weakTypes);
        result.put("message",    String.format(
                "Your weakest area is %s (%.0f%% avg). Book a session with your %s teacher for targeted help.",
                worstName, worstPct, worstName));
        return result;
    }

    // ── Generate AI session preparation plan ──────────────────────────────
    @PostMapping("/prepare")
    public Map<String, Object> prepare(@RequestBody Map<String, Object> body, HttpSession session) {
        String studentId = (String) session.getAttribute("studentId");
        if (studentId == null) return Map.of("error", "Not logged in");
        if (apiKey == null || apiKey.isBlank()) return Map.of("error", "AI not configured");

        String teacherName = (String) body.getOrDefault("teacherName", "my teacher");
        String courseName  = (String) body.getOrDefault("courseName",  "my course");
        String weakAreas   = (String) body.getOrDefault("weakAreas",   "general course content");
        double avgPct      = ((Number) body.getOrDefault("avgPct", 50)).doubleValue();

        String prompt =
                "A university student at Daffodil International University needs help preparing for a counselling session.\n" +
                "Teacher: " + teacherName + "\n" +
                "Struggling course: " + courseName + " (current average: " + Math.round(avgPct) + "%)\n" +
                "Specific weak areas: " + weakAreas + "\n\n" +
                "Generate a concise, structured counselling session preparation guide with these EXACT section headers:\n" +
                "💬 REASON FOR VISIT\n" +
                "📋 SESSION GOAL\n" +
                "❓ KEY QUESTIONS TO ASK\n" +
                "📚 TOPICS TO COVER\n" +
                "✅ WHAT TO BRING\n" +
                "🎯 EXPECTED OUTCOME\n\n" +
                "Rules:\n" +
                "- REASON FOR VISIT must be 2 sentences max — professional, suitable for a booking form.\n" +
                "- Each other section: 2-4 bullet points, concise.\n" +
                "- Total response under 380 words.\n" +
                "- Be practical and specific to the course/topic.";

        try {
            List<Map<String, String>> msgs = List.of(
                    Map.of("role", "system",
                           "content", "You are a university academic counselling assistant. Generate structured, practical session preparation plans for students."),
                    Map.of("role", "user", "content", prompt)
            );

            Map<String, Object> reqBody = new LinkedHashMap<>();
            reqBody.put("model",       GROQ_MODEL);
            reqBody.put("messages",    msgs);
            reqBody.put("max_tokens",  750);
            reqBody.put("temperature", 0.65);

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
            String content = ((String) msg.get("content")).trim();

            // Extract the "Reason for Visit" section for auto-fill
            String reason = extractSection(content, "💬 REASON FOR VISIT", "📋");
            if (reason.isEmpty()) reason = extractSection(content, "REASON FOR VISIT", "📋");

            return Map.of("plan", content, "reason", reason.trim());

        } catch (Exception e) {
            log.error("Counselling prepare error", e);
            return Map.of("error", "Failed to generate plan: " + e.getMessage());
        }
    }

    private String extractSection(String text, String header, String nextHeader) {
        int start = text.indexOf(header);
        if (start < 0) return "";
        start += header.length();
        int end = text.indexOf(nextHeader, start);
        String section = end > start ? text.substring(start, end) : text.substring(start);
        return section.replaceAll("(?m)^[-•*]\\s*", "").trim();
    }
}
