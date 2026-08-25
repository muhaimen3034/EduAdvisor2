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
@RequestMapping("/api/routine")
public class RoutineController {

    @Value("${groq.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String GROQ_URL   = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.1-8b-instant";

    @PostMapping("/generate")
    public Map<String, Object> generate(
            @RequestBody List<Map<String, Object>> courses,
            HttpSession session) {

        if (session.getAttribute("studentId") == null)
            return Map.of("error", "Not logged in");

        if (courses == null || courses.isEmpty())
            return Map.of("error", "No courses provided");

        /* Day numbering from JS: 1=Sat, 2=Sun, 3=Mon, 4=Tue, 5=Wed, 6=Thu, 7=Fri */
        String[] dayNames = {"", "Saturday", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};

        // Group sessions by course code for a clean prompt
        Map<String, List<Map<String, Object>>> byCourse = new LinkedHashMap<>();
        for (Map<String, Object> c : courses) {
            byCourse.computeIfAbsent(str(c, "code"), k -> new ArrayList<>()).add(c);
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<Map<String, Object>>> entry : byCourse.entrySet()) {
            List<Map<String, Object>> sessions = entry.getValue();
            Map<String, Object> first = sessions.get(0);
            sb.append(str(first, "code")).append(": ").append(str(first, "name"))
              .append("  (Teacher: ").append(str(first, "teacher")).append(")\n");
            for (int si = 0; si < sessions.size(); si++) {
                Map<String, Object> sess = sessions.get(si);
                @SuppressWarnings("unchecked")
                List<Object> dayNums = (List<Object>) sess.getOrDefault("days", List.of());
                List<String> dayLabels = new ArrayList<>();
                for (Object d : dayNums) {
                    int idx = ((Number) d).intValue();
                    if (idx >= 1 && idx <= 7) dayLabels.add(dayNames[idx]);
                }
                String room = str(sess, "room");
                sb.append("  Session ").append(si + 1).append(": ")
                  .append(String.join(" & ", dayLabels.isEmpty() ? List.of("Day not set") : dayLabels))
                  .append("  ").append(str(sess, "start")).append("–").append(str(sess, "end"))
                  .append("  Room: ").append(room.isBlank() || room.equals("—") ? "TBD" : room)
                  .append("\n");
            }
            sb.append("\n");
        }

        String prompt = """
                A university student has provided their full semester class schedule below.
                The university week runs Saturday through Friday.

                %s

                Generate a complete, professional semester class routine. Include:
                1. A formatted day-by-day class routine (Saturday → Friday), showing only days that have classes
                2. For each class block: course code, course name, time, room, teacher
                3. Weekly summary stats: total sessions/week, busiest day, free days
                4. 2–3 practical study/time-management tips tailored to this exact schedule

                Use clear section headers and emojis for readability. Keep it concise but complete.
                """.formatted(sb.toString());

        if (apiKey == null || apiKey.isBlank()) {
            return Map.of("routine", buildFallback(courses, dayNames));
        }

        try {
            List<Map<String, String>> msgs = List.of(
                Map.of("role", "system",
                       "content", "You are a helpful university schedule assistant. Format class routines clearly and practically."),
                Map.of("role", "user", "content", prompt)
            );

            Map<String, Object> reqBody = new LinkedHashMap<>();
            reqBody.put("model",       GROQ_MODEL);
            reqBody.put("messages",    msgs);
            reqBody.put("max_tokens",  750);
            reqBody.put("temperature", 0.5);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                GROQ_URL, HttpMethod.POST,
                new HttpEntity<>(reqBody, headers),
                (Class<Map<String, Object>>) (Class<?>) Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.getBody().get("choices");
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            return Map.of("routine", ((String) msg.get("content")).trim());

        } catch (Exception e) {
            log.error("Routine generation error", e);
            return Map.of("routine", buildFallback(courses, dayNames));
        }
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? "—" : v.toString();
    }

    private String buildFallback(List<Map<String, Object>> courses, String[] dayNames) {
        Map<Integer, List<String>> byDay = new TreeMap<>();
        for (int i = 1; i <= 7; i++) byDay.put(i, new ArrayList<>());

        for (Map<String, Object> c : courses) {
            @SuppressWarnings("unchecked")
            List<Object> dayNums = (List<Object>) c.getOrDefault("days", List.of());
            String line = str(c,"start") + "–" + str(c,"end")
                        + "  " + str(c,"code") + " — " + str(c,"name")
                        + "  (Room: " + str(c,"room") + ")";
            for (Object d : dayNums) {
                int idx = ((Number) d).intValue();
                if (idx >= 1 && idx <= 7) byDay.get(idx).add(line);
            }
        }

        StringBuilder sb = new StringBuilder("📅 SEMESTER CLASS ROUTINE\n");
        sb.append("─".repeat(44)).append("\n\n");
        byDay.forEach((day, classes) -> {
            if (!classes.isEmpty()) {
                sb.append("📌 ").append(dayNames[day]).append("\n");
                classes.stream().sorted().forEach(cl -> sb.append("   ").append(cl).append("\n"));
                sb.append("\n");
            }
        });
        int total = courses.stream()
            .mapToInt(c -> { @SuppressWarnings("unchecked") List<?> d = (List<?>) c.getOrDefault("days", List.of()); return d.size(); })
            .sum();
        sb.append("📊 Total classes per week: ").append(total).append("\n");
        sb.append("💡 This weekly pattern repeats throughout the semester.\n");
        sb.append("💡 Tip: Block your study time on your free days.");
        return sb.toString();
    }
}
