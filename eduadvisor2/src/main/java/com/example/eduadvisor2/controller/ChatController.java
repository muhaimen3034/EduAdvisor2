package com.example.eduadvisor2.controller;

import com.example.eduadvisor2.model.*;
import com.example.eduadvisor2.repository.*;
import jakarta.servlet.http.HttpSession;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final EnrollmentRepository           enrollmentRepository;
    private final ExamPaperRepository            examPaperRepository;
    private final ScheduleEventRepository        scheduleEventRepository;
    private final TeacherRepository              teacherRepository;
    private final AssessmentScoreRepository      assessmentScoreRepository;
    private final StudentProfileLinksRepository  studentProfileLinksRepository;
    private final StudentActivityRepository      studentActivityRepository;
    private final FeeStatusRepository            feeStatusRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${groq.api.key:}")
    private String apiKey;

    private static final String GROQ_URL   = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.1-8b-instant";

    private static final String SYSTEM_BASE =
            "You are Nova, a concise academic advisor chatbot in EduAdvisor (DIU student portal).\n\n" +
            "STRICT RULES:\n" +
            "1. Answer ONLY what the student asked. Do not add unrequested information.\n" +
            "2. Be short — 1 sentence for simple questions, bullet points only if the answer has multiple items.\n" +
            "3. Use '- item' bullets when listing things. Use '1.' only for steps.\n" +
            "4. Use **bold** for course codes, scores, and key terms.\n" +
            "5. Never volunteer extra advice, tips, or recommendations unless the student asks.\n" +
            "6. Never repeat the question back. Just answer it directly.\n" +
            "7. Scores: 80+=Excellent, 60-79=Good, 40-59=Needs improvement, below 40=Low.\n\n" +
            "Examples of correct behavior:\n" +
            "Q: what courses am i enrolled in -> list ONLY the course names, nothing else\n" +
            "Q: what is my github score -> one line: 'Your GitHub score is **30/100** (Low).'\n" +
            "Q: what are my weaknesses -> bullet list of weak areas ONLY, no tips unless asked\n\n";

    @PostMapping("/chat/message")
    public Map<String, String> chat(@RequestBody ChatRequest req, HttpSession session) {
        if (session.getAttribute("studentId") == null) {
            return Map.of("reply", "Please log in to use Nova.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            return Map.of("reply", "Nova is not configured yet. Please contact the administrator.");
        }

        String studentId   = (String) session.getAttribute("studentId");
        String studentName = (String) session.getAttribute("studentName");

        try {
            List<Map<String, String>> messages = new ArrayList<>();

            messages.add(Map.of(
                    "role", "system",
                    "content", SYSTEM_BASE + buildStudentContext(studentId, studentName)
            ));

            if (req.getHistory() != null) {
                for (ChatMessage msg : req.getHistory()) {
                    messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
                }
            }

            messages.add(Map.of("role", "user", "content", req.getMessage()));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", GROQ_MODEL);
            body.put("messages", messages);
            body.put("max_tokens", 1024);
            body.put("temperature", 0.7);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    GROQ_URL, HttpMethod.POST, entity,
                    (Class<Map<String, Object>>) (Class<?>) Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices =
                    (List<Map<String, Object>>) response.getBody().get("choices");
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return Map.of("reply", (String) message.get("content"));

        } catch (HttpClientErrorException e) {
            log.error("Groq API error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Map.of("reply", "API error " + e.getStatusCode() + ". Please try again later.");
        } catch (Exception e) {
            log.error("Nova chat error", e);
            return Map.of("reply", "Something went wrong. Please try again.");
        }
    }

    @PostMapping("/chat/parse-booking")
    public Map<String, Object> parseBooking(@RequestBody Map<String, String> req, HttpSession session) {
        if (session.getAttribute("studentId") == null) return Map.of("intent", "other");
        if (apiKey == null || apiKey.isBlank())        return Map.of("intent", "other");

        String voiceText = req.getOrDefault("text", "");

        List<Teacher> teachers = teacherRepository.findAll();
        StringBuilder teacherListSb = new StringBuilder();
        for (Teacher t : teachers) {
            teacherListSb.append("  - ").append(t.getName())
                    .append(" | last name: ").append(t.getName().replaceAll("^.* ", ""))
                    .append(" | dept: ").append(t.getDepartment()).append("\n");
        }

        String today     = LocalDate.now().toString();
        String dayOfWeek = LocalDate.now().getDayOfWeek().toString();

        String system =
                "You extract booking details from a student's voice command.\n" +
                "This IS an appointment booking request — do NOT question the intent.\n\n" +
                "Today: " + today + " (" + dayOfWeek + ").\n\n" +
                "Available teachers:\n" + teacherListSb +
                "\nRules:\n" +
                "- Match teacher names loosely: 'nasimul sir' = 'Nasimul K. Sohel', " +
                "'sarwar sir' = 'Sarwar Hossain', 'azam sir' = 'Md. Azam', " +
                "'mahbub sir' = 'Mahbub Hossain', 'mehedi sir' = 'Mehedi Hasan', " +
                "'fatema mam' = 'Dr. Fatema Khanam', 'dr ahmed' = 'Dr. Ahmed', " +
                "'dr rahman' = 'Dr. Rahman'. Use the last name or first name to match.\n" +
                "- Convert relative time: 'tomorrow' = next day, 'Monday' = next Monday, etc.\n" +
                "- Format slot as: 'DayName H:MM AM/PM', e.g. 'Monday 3:00 PM'\n\n" +
                "Return ONLY this JSON (no markdown, no extra text):\n" +
                "{\"intent\":\"booking\",\"teacherName\":\"exact name from list\",\"slot\":\"Day H:MM AM/PM\",\"reason\":\"\"}";

        try {
            List<Map<String, String>> msgs = List.of(
                    Map.of("role", "system", "content", system),
                    Map.of("role", "user",   "content", voiceText)
            );
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", GROQ_MODEL);
            body.put("messages", msgs);
            body.put("max_tokens", 150);
            body.put("temperature", 0.1);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    GROQ_URL, HttpMethod.POST, new HttpEntity<>(body, headers),
                    (Class<Map<String, Object>>) (Class<?>) Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.getBody().get("choices");
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            String content = ((String) msg.get("content")).trim();

            if (content.startsWith("```")) {
                content = content.replaceAll("```[a-zA-Z]*\\n?", "").replace("```", "").trim();
            }

            String normalised = content.replaceAll("\"\\s*:\\s*\"", "\":\"");
            if (normalised.contains("\"intent\":\"booking\"")) {
                Map<String, Object> result = new HashMap<>();
                result.put("intent",      "booking");
                result.put("teacherName", extractJsonString(normalised, "teacherName"));
                result.put("slot",        extractJsonString(normalised, "slot"));
                result.put("reason",      extractJsonString(normalised, "reason"));
                log.info("Booking parsed — teacher={} slot={}", result.get("teacherName"), result.get("slot"));
                return result;
            }
            log.info("parse-booking: not a booking intent. Raw: {}", content);
            return Map.of("intent", "other");

        } catch (Exception e) {
            log.error("parse-booking error", e);
            return Map.of("intent", "other");
        }
    }

    private static String extractJsonString(String json, String field) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) return "";
        start += needle.length();
        int end = json.indexOf("\"", start);
        return end < 0 ? "" : json.substring(start, end);
    }

    private String buildStudentContext(String studentId, String studentName) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== STUDENT DATA ===\n");
        sb.append("Name: ").append(studentName != null ? studentName : "Unknown").append("\n");
        sb.append("ID:   ").append(studentId).append("\n\n");

        // ── Fee status ────────────────────────────────────────────────────────
        feeStatusRepository.findByStudentId(studentId).ifPresent(fee ->
            sb.append("FEE STATUS: ").append(fee.getStatus()).append("\n\n")
        );

        // ── Enrolled courses ──────────────────────────────────────────────────
        List<Enrollment> enrollments = enrollmentRepository.findByStudentId(studentId);
        if (enrollments.isEmpty()) {
            sb.append("ENROLLED COURSES: None yet.\n");
        } else {
            sb.append("ENROLLED COURSES (").append(enrollments.size()).append("):\n");
            Set<String> teacherNames = new LinkedHashSet<>();
            for (Enrollment e : enrollments) {
                var c = e.getCourse();
                sb.append("  • ").append(c.getCode()).append(" — ").append(c.getName())
                  .append("\n      Instructor: ").append(c.getInstructor())
                  .append(" | Room: ").append(c.getRoom())
                  .append(" | Days: ").append(expandDays(c.getDays()))
                  .append(" | Time: ").append(c.getStartTime()).append("–").append(c.getEndTime())
                  .append(" | Credits: ").append(c.getCredits()).append("\n");
                if (c.getInstructor() != null) teacherNames.add(c.getInstructor());
            }
            sb.append("\nTEACHERS (").append(teacherNames.size()).append("):\n");
            teacherNames.forEach(t -> sb.append("  • ").append(t).append("\n"));
        }

        // ── Assessment scores / grades ────────────────────────────────────────
        List<AssessmentScore> scores = assessmentScoreRepository
                .findByStudentIdOrderByRecordedAtDesc(studentId);
        if (!scores.isEmpty()) {
            sb.append("\nASSESSMENT SCORES (").append(scores.size()).append(" records):\n");
            Map<String, List<AssessmentScore>> byCourse = scores.stream()
                    .collect(Collectors.groupingBy(
                            s -> s.getCourseCode() + " — " + s.getCourseName(),
                            LinkedHashMap::new, Collectors.toList()));
            for (var entry : byCourse.entrySet()) {
                sb.append("  ").append(entry.getKey()).append(":\n");
                for (AssessmentScore s : entry.getValue()) {
                    double pct = s.getMaxScore() > 0 ? (s.getScore() / s.getMaxScore() * 100) : 0;
                    sb.append("    – ").append(s.getAssessmentType())
                      .append(": ").append(String.format("%.1f", s.getScore()))
                      .append("/").append(String.format("%.0f", s.getMaxScore()))
                      .append(String.format(" (%.0f%%)", pct));
                    if (s.getNotes() != null && !s.getNotes().isBlank())
                        sb.append(" [").append(s.getNotes()).append("]");
                    sb.append("\n");
                }
            }
        } else {
            sb.append("\nASSESSMENT SCORES: No grades recorded yet.\n");
        }

        // ── Submitted papers ──────────────────────────────────────────────────
        List<ExamPaper> papers = examPaperRepository.findByStudentIdOrderBySubmittedAtDesc(studentId);
        sb.append("\nSUBMITTED PAPERS (").append(papers.size()).append("):\n");
        if (papers.isEmpty()) {
            sb.append("  None yet.\n");
        } else {
            papers.stream().limit(5).forEach(p ->
                sb.append("  • [").append(p.getType()).append("] ").append(p.getTitle())
                  .append(" — by ").append(p.getTeacherName())
                  .append(" on ").append(p.getSubmittedAt().toLocalDate()).append("\n")
            );
        }

        // ── Schedule events ───────────────────────────────────────────────────
        List<ScheduleEvent> events = scheduleEventRepository.findByStudentIdOrderByDateAsc(studentId);
        sb.append("\nSCHEDULE EVENTS (").append(events.size()).append("):\n");
        if (events.isEmpty()) {
            sb.append("  None saved.\n");
        } else {
            events.stream().limit(5).forEach(ev ->
                sb.append("  • ").append(ev.getDate())
                  .append(ev.getTime() != null ? " " + ev.getTime() : "")
                  .append(" — ").append(ev.getTitle())
                  .append(" [").append(ev.getType()).append("]\n")
            );
        }

        // ── Learning gap / profile analysis ──────────────────────────────────
        studentProfileLinksRepository.findById(studentId).ifPresent(p -> {
            sb.append("\nLEARNING GAP ANALYSIS:\n");
            sb.append("  LinkedIn Profile:   ").append(scoreLabel(p.getLinkedinScore())).append("\n");
            sb.append("  Network Strength:   ").append(scoreLabel(p.getNetworkScore())).append("\n");
            sb.append("  Content Activity:   ").append(scoreLabel(p.getContentScore())).append("\n");
            sb.append("  Projects:           ").append(scoreLabel(p.getProjectsScore())).append("\n");
            sb.append("  Seminars/Workshops: ").append(scoreLabel(p.getSeminarsScore())).append("\n");
            sb.append("  Bootcamps:          ").append(scoreLabel(p.getBootcampsScore())).append("\n");
            sb.append("  Research:           ").append(scoreLabel(p.getResearchScore())).append("\n");
            sb.append("  GitHub:             ").append(scoreLabel(p.getGithubScore())).append("\n");
            sb.append("  Technical Skills:   ").append(scoreLabel(p.getTechnicalScore())).append("\n");
            if (p.getGithubLanguages() != null && !p.getGithubLanguages().isBlank())
                sb.append("  GitHub languages: ").append(p.getGithubLanguages()).append("\n");
            if (p.getLinkedinSummary() != null && !p.getLinkedinSummary().isBlank())
                sb.append("  LinkedIn summary: ").append(p.getLinkedinSummary()).append("\n");
            if (p.getGithubSummary() != null && !p.getGithubSummary().isBlank())
                sb.append("  GitHub summary: ").append(p.getGithubSummary()).append("\n");
            if (p.getStrengthsJson() != null && !p.getStrengthsJson().isBlank())
                sb.append("  Strengths: ").append(p.getStrengthsJson()).append("\n");
            if (p.getGapsJson() != null && !p.getGapsJson().isBlank())
                sb.append("  Gaps: ").append(p.getGapsJson()).append("\n");
            if (p.getRecommendationsJson() != null && !p.getRecommendationsJson().isBlank())
                sb.append("  AI Recommendations: ").append(p.getRecommendationsJson()).append("\n");
        });

        // ── Student activities ────────────────────────────────────────────────
        studentActivityRepository.findById(studentId).ifPresent(a -> {
            sb.append("\nSTUDENT ACTIVITIES:\n");
            appendActivity(sb, "Club involvement", a.getClubScore(), a.getClubDescription());
            appendActivity(sb, "Volunteering",     a.getVolunteeringScore(), a.getVolunteeringDescription());
            appendActivity(sb, "Technical Skills", a.getSkillsScore(), a.getSkillsDescription());
            appendActivity(sb, "Research/Projects",a.getResearchScore(), a.getResearchDescription());
            appendActivity(sb, "Leadership",       a.getLeadershipScore(), a.getLeadershipDescription());
        });

        sb.append("=== END OF STUDENT DATA ===\n");
        return sb.toString();
    }

    private static void appendActivity(StringBuilder sb, String label, Integer score, String desc) {
        sb.append("  ").append(label).append(": ").append(scoreLabel(score));
        if (desc != null && !desc.isBlank()) sb.append(" — ").append(desc);
        sb.append("\n");
    }

    private static String scoreLabel(Integer score) {
        if (score == null) return "Not analyzed yet";
        String level = score >= 80 ? "Excellent" : score >= 60 ? "Good" : score >= 40 ? "Needs improvement" : "Low";
        return score + "/100 (" + level + ")";
    }

    private static String expandDays(String days) {
        if (days == null || days.isBlank()) return "—";
        String[] names = {"", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        return Arrays.stream(days.split(","))
                .map(String::trim)
                .map(d -> {
                    try { int i = Integer.parseInt(d); return (i >= 1 && i <= 7) ? names[i] : d; }
                    catch (NumberFormatException e) { return d; }
                })
                .collect(Collectors.joining(", "));
    }

    @Data public static class ChatRequest  { private String message; private List<ChatMessage> history; }
    @Data public static class ChatMessage  { private String role;    private String content;            }
}
