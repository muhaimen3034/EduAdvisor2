package com.example.eduadvisor2.controller;

import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/credit-transfer")
public class CreditTransferAIController {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${groq.api.key:}")
    private String apiKey;

    private static final String GROQ_URL   = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.1-8b-instant";

    /**
     * mode = "eligibility" | "remarks" | "equivalency" | "chat"
     * formData = map of all form fields from the page
     * question = free-form question (used in chat mode)
     */
    @PostMapping("/ai")
    public Map<String, String> analyze(@RequestBody Map<String, Object> req, HttpSession session) {
        if (session.getAttribute("studentId") == null) {
            return Map.of("reply", "Please log in to use the AI Advisor.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            return Map.of("reply", "AI Advisor is not configured. Please contact the administrator.");
        }

        String mode     = (String) req.getOrDefault("mode", "chat");
        String question = (String) req.getOrDefault("question", "");

        @SuppressWarnings("unchecked")
        Map<String, Object> formData = (Map<String, Object>) req.getOrDefault("formData", Map.of());

        String systemPrompt = buildSystemPrompt(mode, formData);
        String userMessage  = buildUserMessage(mode, formData, question);

        try {
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user",   "content", userMessage)
            );

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model",       GROQ_MODEL);
            body.put("messages",    messages);
            body.put("max_tokens",  1200);
            body.put("temperature", 0.6);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    GROQ_URL, HttpMethod.POST, new HttpEntity<>(body, headers),
                    (Class<Map<String, Object>>) (Class<?>) Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices =
                    (List<Map<String, Object>>) response.getBody().get("choices");
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return Map.of("reply", (String) message.get("content"));

        } catch (Exception e) {
            log.error("CreditTransfer AI error", e);
            return Map.of("reply", "AI error: " + e.getMessage());
        }
    }

    private String buildSystemPrompt(String mode, Map<String, Object> form) {
        String base =
                "You are an expert Credit Transfer Advisor at Daffodil International University (DIU), Bangladesh. " +
                "You help students understand the credit transfer process, evaluate their eligibility, " +
                "and write strong application remarks. " +
                "Be concise, practical, and encouraging. Use bullet points where helpful. " +
                "DIU follows these transfer rules: minimum grade C (2.0/4.0), courses must have ≥70% syllabus overlap, " +
                "credits must be earned within the last 7 years, maximum 60 credits transferable, " +
                "source institution must be accredited.";

        return switch (mode) {
            case "eligibility" -> base +
                    "\n\nFor eligibility checks: assess each criterion, flag any red flags with ⚠️, " +
                    "give an overall likelihood (High/Medium/Low), and list specific tips to strengthen the application.";
            case "remarks" -> base +
                    "\n\nFor remarks drafting: write 2-3 professional paragraphs suitable for a formal " +
                    "credit transfer application. Tone should be formal but sincere. Include the student's motivation, " +
                    "the academic value of the transfer, and a commitment statement. Keep it under 200 words.";
            case "equivalency" -> base +
                    "\n\nFor course equivalency: suggest the most likely DIU/CSE equivalent course for each submitted course. " +
                    "Format: '• [Source Course] → [Suggested DIU Equivalent] (Confidence: High/Medium/Low)' " +
                    "followed by a one-line reason. If no clear match, say 'Review Required'.";
            default -> base +
                    "\n\nAnswer the student's question clearly. If it's not about credit transfer, " +
                    "gently redirect them to the relevant EduAdvisor section.";
        };
    }

    private String buildUserMessage(String mode, Map<String, Object> form, String question) {
        String formSummary = buildFormSummary(form);

        return switch (mode) {
            case "eligibility" ->
                    "Please evaluate the eligibility of this credit transfer request:\n\n" + formSummary +
                    "\n\nProvide: (1) eligibility assessment per criterion, " +
                    "(2) any red flags, (3) overall likelihood of approval, (4) tips to improve.";
            case "remarks" ->
                    "Please draft professional remarks for this credit transfer application:\n\n" + formSummary +
                    "\n\nWrite 2-3 formal paragraphs that I can paste into the 'Additional Notes' field.";
            case "equivalency" ->
                    "Please suggest DIU course equivalencies for these transferred courses:\n\n" + formSummary;
            default ->
                    question.isBlank()
                    ? "Tell me the most important things to know about the credit transfer process at DIU."
                    : question + (formSummary.isBlank() ? "" : "\n\nMy transfer details:\n" + formSummary);
        };
    }

    private String buildFormSummary(Map<String, Object> form) {
        if (form == null || form.isEmpty()) return "(No form data provided yet.)";
        StringBuilder sb = new StringBuilder();

        append(sb, form, "studentName",        "Student Name");
        append(sb, form, "currentProgram",     "Current Program");
        append(sb, form, "currentYear",        "Current Year");
        append(sb, form, "gpa",                "Current GPA");
        append(sb, form, "sourceInstitution",  "Source Institution");
        append(sb, form, "sourceCountry",      "Country");
        append(sb, form, "gradingSystem",      "Grading System");
        append(sb, form, "sourceProgramStudied","Program Studied");
        append(sb, form, "targetDepartment",   "Target Department");
        append(sb, form, "targetProgram",      "Target Program");
        append(sb, form, "entryLevel",         "Entry Level");
        append(sb, form, "transferSemester",   "Transfer Semester");
        append(sb, form, "notes",              "Student Remarks");

        Object courses = form.get("courses");
        if (courses instanceof List<?> list && !list.isEmpty()) {
            sb.append("Courses to Transfer:\n");
            for (Object c : list) {
                if (c instanceof Map<?, ?> raw) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) raw;
                    sb.append("  • ").append(m.getOrDefault("sourceName", ""))
                      .append(" → ").append(m.getOrDefault("targetName", "(TBD)"))
                      .append(" [").append(m.getOrDefault("credits", "?")).append(" credits, grade: ")
                      .append(m.getOrDefault("grade", "?")).append("]\n");
                }
            }
        }
        return sb.toString();
    }

    private void append(StringBuilder sb, Map<String, Object> map, String key, String label) {
        Object val = map.get(key);
        if (val != null && !val.toString().isBlank()) {
            sb.append(label).append(": ").append(val).append("\n");
        }
    }
}
