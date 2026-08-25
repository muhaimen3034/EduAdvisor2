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
@RequestMapping("/api/discussion")
public class DiscussionAIController {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${groq.api.key:}")
    private String apiKey;

    private static final String GROQ_URL   = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.1-8b-instant";

    @PostMapping("/ai-answer")
    public ResponseEntity<?> aiAnswer(@RequestBody Map<String, String> body, HttpSession session) {
        boolean isStudent = session.getAttribute("studentId") != null;
        boolean isTeacher = session.getAttribute("teacherId") != null;
        if (!isStudent && !isTeacher) return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        if (apiKey == null || apiKey.isBlank()) return ResponseEntity.ok(Map.of("error", "AI not configured"));

        String question = body.getOrDefault("question", "").trim();
        String mode     = body.getOrDefault("mode", "student"); // "student" | "teacher"

        if (question.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "Question is required"));

        String systemPrompt = mode.equals("teacher")
                ? "You are a knowledgeable university teacher at Daffodil International University. " +
                  "Draft a clear, educational, and well-structured answer to a student's question. " +
                  "Be thorough yet concise. Use examples where helpful. " +
                  "The teacher will review and edit this draft before sending."
                : "You are a helpful AI academic assistant at Daffodil International University. " +
                  "Answer the student's question clearly, accurately, and in an encouraging tone. " +
                  "Use simple language, step-by-step explanations when needed, and practical examples.";

        String userPrompt = mode.equals("teacher")
                ? "A student sent the following question to their teacher. Draft a complete teacher reply:\n\n" + question
                : "A university student has this question. Provide a clear, helpful academic answer:\n\n" + question;

        try {
            List<Map<String, String>> msgs = List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user",   "content", userPrompt)
            );

            Map<String, Object> reqBody = new LinkedHashMap<>();
            reqBody.put("model",       GROQ_MODEL);
            reqBody.put("messages",    msgs);
            reqBody.put("max_tokens",  700);
            reqBody.put("temperature", 0.5);

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

            return ResponseEntity.ok(Map.of("answer", content));

        } catch (Exception e) {
            log.error("Discussion AI error", e);
            return ResponseEntity.ok(Map.of("error", "Failed to generate answer: " + e.getMessage()));
        }
    }
}
