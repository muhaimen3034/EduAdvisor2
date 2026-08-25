package com.example.eduadvisor2.controller;

import com.example.eduadvisor2.model.AssessmentScore;
import com.example.eduadvisor2.model.Enrollment;
import com.example.eduadvisor2.model.ExamPaper;
import com.example.eduadvisor2.model.Student;
import com.example.eduadvisor2.repository.AssessmentScoreRepository;
import com.example.eduadvisor2.repository.EnrollmentRepository;
import com.example.eduadvisor2.repository.ExamPaperRepository;
import com.example.eduadvisor2.service.StudentService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/advisor")
@RequiredArgsConstructor
public class AdvisorController {

    @Value("${groq.api.key:}")
    private String apiKey;

    private final StudentService            studentService;
    private final EnrollmentRepository      enrollmentRepository;
    private final AssessmentScoreRepository assessmentScoreRepository;
    private final ExamPaperRepository       examPaperRepository;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String GROQ_URL   = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.1-8b-instant";

    @PostMapping("/analyze/{studentId}")
    public Map<String, Object> analyze(@PathVariable String studentId, HttpSession session) {
        boolean isCoordinator = session.getAttribute("coordinatorId") != null;
        String advisorBatch   = (String) session.getAttribute("advisorBatch");
        if (!isCoordinator && advisorBatch == null)
            return Map.of("error", "Not authorized");
        if (advisorBatch != null && !studentId.startsWith(advisorBatch + "-"))
            return Map.of("error", "Student not in your batch");

        Student student = studentService.findByStudentId(studentId).orElse(null);
        if (student == null) return Map.of("error", "Student not found");

        List<Enrollment>      enrollments = enrollmentRepository.findByStudentId(studentId);
        List<AssessmentScore> scores      = assessmentScoreRepository.findByStudentIdOrderByRecordedAtDesc(studentId);
        List<ExamPaper>       papers      = examPaperRepository.findByStudentIdOrderBySubmittedAtDesc(studentId);

        String prompt = buildPrompt(student, enrollments, scores, papers);

        if (apiKey == null || apiKey.isBlank()) {
            return Map.of("analysis", buildFallback(student, scores));
        }

        try {
            List<Map<String, String>> msgs = List.of(
                Map.of("role", "system",
                    "content", "You are a precise, data-driven academic advisor AI. Analyze student data and give specific, actionable feedback."),
                Map.of("role", "user", "content", prompt)
            );
            Map<String, Object> reqBody = new LinkedHashMap<>();
            reqBody.put("model",       GROQ_MODEL);
            reqBody.put("messages",    msgs);
            reqBody.put("max_tokens",  750);
            reqBody.put("temperature", 0.4);

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
            return Map.of("analysis", ((String) msg.get("content")).trim());

        } catch (Exception e) {
            log.error("Advisor AI error for student {}: {}", studentId, e.getMessage());
            return Map.of("analysis", buildFallback(student, scores));
        }
    }

    private String buildPrompt(Student s, List<Enrollment> enrollments,
                                List<AssessmentScore> scores, List<ExamPaper> papers) {
        StringBuilder sb = new StringBuilder();
        sb.append("Student        : ").append(s.getName()).append(" (ID: ").append(s.getStudentId()).append(")\n");
        sb.append("Program        : ").append(nvl(s.getProgram())).append("\n");
        sb.append("Academic Year  : ").append(nvl(s.getAcademicYear())).append("\n");
        sb.append("GPA            : ").append(s.getGpa() != null ? s.getGpa() : "—").append(" / 4.0\n");
        sb.append("Credits Done   : ").append(s.getCreditsCompleted() != null ? s.getCreditsCompleted() : 0).append("\n\n");

        if (!enrollments.isEmpty()) {
            sb.append("Enrolled Courses (CLOSED = mandatory batch credit; EXTRA = optional batch credit; no tag = general):\n");
            enrollments.forEach(e -> {
                String tag = e.getCourse().getCreditType() != null
                    ? " [" + e.getCourse().getCreditType() + "]" : "";
                sb.append("  • ").append(e.getCourse().getCode())
                  .append(": ").append(e.getCourse().getName()).append(tag).append("\n");
            });
            sb.append("\n");
        } else {
            sb.append("Enrolled Courses: None recorded\n\n");
        }

        if (!scores.isEmpty()) {
            // Group by course
            Map<String, List<AssessmentScore>> byCourse = new LinkedHashMap<>();
            scores.forEach(sc -> byCourse
                .computeIfAbsent(sc.getCourseCode() + " — " + sc.getCourseName(), k -> new ArrayList<>())
                .add(sc));
            sb.append("Assessment Scores:\n");
            byCourse.forEach((course, list) -> {
                sb.append("  ").append(course).append(":\n");
                list.forEach(sc -> {
                    double pct = sc.getMaxScore() > 0 ? sc.getScore() / sc.getMaxScore() * 100 : 0;
                    sb.append("    ").append(sc.getAssessmentType())
                      .append(": ").append(String.format("%.1f / %.1f  (%.0f%%)", sc.getScore(), sc.getMaxScore(), pct));
                    if (sc.getNotes() != null && !sc.getNotes().isBlank())
                        sb.append("  [Note: ").append(sc.getNotes()).append("]");
                    sb.append("\n");
                });
            });
            sb.append("\n");
        } else {
            sb.append("Assessment Scores: None recorded\n\n");
        }

        if (!papers.isEmpty()) {
            sb.append("Exam/Assignment Papers Submitted by Teachers:\n");
            papers.forEach(p -> sb.append("  • ").append(p.getType()).append(": \"").append(p.getTitle())
                .append("\" — by ").append(p.getTeacherName()).append("\n"));
            sb.append("\n");
        }

        sb.append("""
            Note on courses: CLOSED credit courses are mandatory for the student's batch curriculum \
and carry higher academic weight. EXTRA courses are optional enrichment. Prioritise performance \
in CLOSED credit courses when assessing and advising.

            Based on all the above data, provide a structured academic analysis:

            1. 🎯 Overall Assessment — rate STRONG / AVERAGE / WEAK with a one-sentence justification citing the GPA
            2. ✅ Strong Areas — which courses or assessment types show good performance (give specific percentages)
            3. ⚠️ Weak Areas — which courses or assessment types need attention (cite specific low scores/percentages)
            4. 📋 Recommendations — 3–4 targeted, actionable suggestions for this specific student
            5. 🚨 Priority Alert — the single most urgent issue the advisor must address

            Be data-driven. Mention actual course names and score percentages. Under 400 words.
            """);

        return sb.toString();
    }

    private String buildFallback(Student s, List<AssessmentScore> scores) {
        double gpa = s.getGpa() != null ? s.getGpa() : 0.0;
        String level = gpa >= 3.5 ? "STRONG" : gpa >= 2.5 ? "AVERAGE" : "WEAK";
        StringBuilder sb = new StringBuilder();
        sb.append("🎯 Overall Assessment: ").append(level).append("\n");
        sb.append("GPA: ").append(gpa).append(" / 4.0 — ");
        if (gpa >= 3.5)      sb.append("Performing well above average.\n");
        else if (gpa >= 2.5) sb.append("Performing at an acceptable level with room for improvement.\n");
        else                  sb.append("Needs immediate academic intervention.\n");

        if (!scores.isEmpty()) {
            OptionalDouble avg = scores.stream()
                .filter(sc -> sc.getMaxScore() > 0)
                .mapToDouble(sc -> sc.getScore() / sc.getMaxScore() * 100)
                .average();
            if (avg.isPresent())
                sb.append("\n📊 Average score across all assessments: ")
                  .append(String.format("%.1f%%\n", avg.getAsDouble()));

            // Find lowest-scoring course
            scores.stream()
                .filter(sc -> sc.getMaxScore() > 0)
                .min(Comparator.comparingDouble(sc -> sc.getScore() / sc.getMaxScore()))
                .ifPresent(sc -> sb.append("⚠️ Lowest score: ")
                    .append(sc.getCourseCode()).append(" — ").append(sc.getAssessmentType())
                    .append(" (").append(String.format("%.0f%%", sc.getScore() / sc.getMaxScore() * 100)).append(")\n"));
        }

        sb.append("\n📋 Recommendation: Schedule a counselling session to review academic progress in detail.");
        return sb.toString();
    }

    private String nvl(String v) { return v != null ? v : "—"; }
}
