package com.example.eduadvisor2.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Fetches real LinkedIn profile data via the linkedin-api Python package (free, unofficial).
 * Requires linkedin.fetch.email and linkedin.fetch.password in application.properties.
 * Install once: pip install linkedin-api
 */
@Slf4j
@Service
public class LinkedInDataService {

    @Value("${linkedin.fetch.email:}")
    private String liEmail;

    @Value("${linkedin.fetch.password:}")
    private String liPassword;

    private static final String SCRIPT_PATH = "scripts/fetch_linkedin.py";
    private final ObjectMapper mapper = new ObjectMapper();

    public boolean isConfigured() {
        return liEmail != null && !liEmail.isBlank()
            && liPassword != null && !liPassword.isBlank();
    }

    /**
     * Calls the Python script to fetch the LinkedIn profile.
     * Returns null if not configured or fetch fails.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchProfile(String linkedinUrl) {
        if (!isConfigured()) {
            log.debug("LinkedIn credentials not configured — skipping fetch");
            return null;
        }

        // Resolve the script relative to the working directory
        File script = new File(SCRIPT_PATH);
        if (!script.exists()) {
            // try absolute path relative to JAR location
            script = new File(System.getProperty("user.dir"), SCRIPT_PATH);
        }
        if (!script.exists()) {
            log.warn("fetch_linkedin.py not found at {}", script.getAbsolutePath());
            return null;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("python", script.getAbsolutePath(), linkedinUrl);
            pb.environment().put("LI_EMAIL",    liEmail);
            pb.environment().put("LI_PASSWORD", liPassword);
            pb.redirectErrorStream(false); // keep stderr separate

            Process proc = pb.start();
            boolean finished = proc.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                log.warn("LinkedIn fetch timed out for {}", linkedinUrl);
                return null;
            }

            String stdout = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String stderr = new String(proc.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();

            if (proc.exitValue() != 0 || stdout.isEmpty()) {
                log.warn("LinkedIn fetch failed for {}: {}", linkedinUrl, stderr);
                return null;
            }

            Map<String, Object> result = mapper.readValue(stdout, new TypeReference<Map<String,Object>>() {});
            if (result.containsKey("error")) {
                log.warn("LinkedIn API error for {}: {}", linkedinUrl, result.get("error"));
                return null;
            }

            log.info("LinkedIn profile fetched successfully for {}", linkedinUrl);
            return result;

        } catch (Exception e) {
            log.warn("LinkedIn fetch exception for {}: {}", linkedinUrl, e.getMessage());
            return null;
        }
    }

    /**
     * Builds a human-readable summary of the LinkedIn profile for the Groq prompt.
     */
    @SuppressWarnings("unchecked")
    public String buildProfileContext(Map<String, Object> profile) {
        if (profile == null) return "";
        StringBuilder sb = new StringBuilder();

        String name        = str(profile.get("full_name"));
        String headline    = str(profile.get("headline"));
        String summary     = str(profile.get("summary"));
        int    connections = toInt(profile.get("connections"));

        if (!name.isEmpty())     sb.append("Name: ").append(name).append("\n");
        if (!headline.isEmpty()) sb.append("Headline: ").append(headline).append("\n");
        sb.append("Connections: ").append(connections > 0 ? connections + "+" : "not shown").append("\n");
        if (!summary.isEmpty())  sb.append("About: ").append(truncate(summary, 300)).append("\n");

        // Projects
        List<Map<String,Object>> projects = getList(profile, "accomplishment_projects");
        sb.append("\nPROJECTS (").append(projects.size()).append("):\n");
        if (projects.isEmpty()) {
            sb.append("  None\n");
        } else {
            for (int i = 0; i < projects.size(); i++) {
                Map<String,Object> p = projects.get(i);
                sb.append("  ").append(i + 1).append(". ").append(str(p.get("title")));
                String desc = str(p.get("description"));
                if (!desc.isEmpty()) sb.append(" — ").append(truncate(desc, 150));
                sb.append("\n");
            }
        }

        // Certifications
        List<Map<String,Object>> certs = getList(profile, "certifications");
        sb.append("\nCERTIFICATIONS (").append(certs.size()).append("):\n");
        if (certs.isEmpty()) {
            sb.append("  None\n");
        } else {
            for (Map<String,Object> c : certs) {
                sb.append("  - ").append(str(c.get("name")));
                String auth = str(c.get("authority"));
                if (!auth.isEmpty()) sb.append(" | ").append(auth);
                Object starts = c.get("starts_at");
                if (starts instanceof Map) {
                    Object yr = ((Map<?,?>)starts).get("year");
                    if (yr != null && !yr.toString().isEmpty()) sb.append(" (").append(yr).append(")");
                }
                sb.append("\n");
            }
        }

        // Publications
        List<Map<String,Object>> pubs = getList(profile, "accomplishment_publications");
        sb.append("\nPUBLICATIONS / RESEARCH (").append(pubs.size()).append("):\n");
        if (pubs.isEmpty()) {
            sb.append("  None\n");
        } else {
            for (Map<String,Object> p : pubs) {
                sb.append("  - ").append(str(p.get("name")));
                String pub = str(p.get("publisher"));
                if (!pub.isEmpty()) sb.append(" | ").append(pub);
                sb.append("\n");
            }
        }

        // Courses
        List<Map<String,Object>> courses = getList(profile, "accomplishment_courses");
        if (!courses.isEmpty()) {
            sb.append("\nCOURSES (").append(courses.size()).append("):\n");
            courses.stream().limit(10).forEach(c -> sb.append("  - ").append(str(c.get("name"))).append("\n"));
        }

        // Activities / Posts
        List<Map<String,Object>> activities = getList(profile, "activities");
        sb.append("\nRECENT POSTS/ACTIVITIES (").append(activities.size()).append("):\n");
        if (activities.isEmpty()) {
            sb.append("  None\n");
        } else {
            activities.stream().limit(8).forEach(a ->
                sb.append("  - [").append(str(a.get("activity_status"))).append("] ").append(truncate(str(a.get("title")), 100)).append("\n"));
        }

        // Skills
        Object skillsObj = profile.get("skills");
        List<String> skills = new ArrayList<>();
        if (skillsObj instanceof List) {
            for (Object o : (List<?>) skillsObj) {
                skills.add(o != null ? o.toString() : "");
            }
        }
        if (!skills.isEmpty()) {
            sb.append("\nSKILLS (").append(skills.size()).append("): ")
              .append(String.join(", ", skills.subList(0, Math.min(25, skills.size())))).append("\n");
        }

        // Experience
        List<Map<String,Object>> exps = getList(profile, "experiences");
        if (!exps.isEmpty()) {
            sb.append("\nEXPERIENCE:\n");
            exps.stream().limit(5).forEach(e ->
                sb.append("  - ").append(str(e.get("title"))).append(" @ ").append(str(e.get("company"))).append("\n"));
        }

        // Education
        List<Map<String,Object>> edu = getList(profile, "education");
        if (!edu.isEmpty()) {
            sb.append("\nEDUCATION:\n");
            edu.stream().limit(3).forEach(e ->
                sb.append("  - ").append(str(e.get("degree_name"))).append(" @ ").append(str(e.get("school"))).append("\n"));
        }

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> getList(Map<String,Object> m, String key) {
        Object v = m.get(key);
        if (!(v instanceof List)) return List.of();
        List<?> raw = (List<?>) v;
        List<Map<String,Object>> result = new ArrayList<>();
        for (Object o : raw) {
            if (o instanceof Map) result.add((Map<String,Object>) o);
        }
        return result;
    }

    private String str(Object v) { return v != null ? v.toString().trim() : ""; }
    private int toInt(Object v) {
        if (v == null) return 0;
        try { return ((Number) v).intValue(); } catch (Exception e) { return 0; }
    }
    private String truncate(String s, int max) {
        if (s == null || s.isEmpty()) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
