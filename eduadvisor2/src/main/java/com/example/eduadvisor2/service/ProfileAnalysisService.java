package com.example.eduadvisor2.service;

import com.example.eduadvisor2.model.StudentProfileLinks;
import com.example.eduadvisor2.repository.StudentProfileLinksRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileAnalysisService {

    private final StudentProfileLinksRepository profileRepo;
    private final LinkedInDataService linkedInDataService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${groq.api.key:}")
    private String groqKey;

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

    // ── Public entry point ────────────────────────────────────────────────
    public StudentProfileLinks analyzeAndSave(String studentId, String linkedinUrl, String githubUrl, String activityDescription) {
        StudentProfileLinks p = profileRepo.findById(studentId)
            .orElse(StudentProfileLinks.builder().studentId(studentId).build());
        p.setLinkedinUrl(linkedinUrl);
        p.setGithubUrl(githubUrl);
        if (activityDescription != null && !activityDescription.isBlank())
            p.setActivityDescription(activityDescription.trim());
        p.setAnalysisStatus("ANALYZING");
        profileRepo.save(p);

        try {
            // ── Step 1: LinkedIn data (Proxycurl) — fetch fresh, cache result ─
            Map<String, Object> liData = null;
            if (linkedInDataService.isConfigured()) {
                liData = linkedInDataService.fetchProfile(linkedinUrl);
                if (liData != null) {
                    try { p.setLinkedinDataJson(objectMapper.writeValueAsString(liData)); }
                    catch (Exception ignore) {}
                }
            }
            // Fall back to cached JSON if Proxycurl not configured / failed
            if (liData == null && p.getLinkedinDataJson() != null) {
                try { liData = objectMapper.readValue(p.getLinkedinDataJson(), new TypeReference<Map<String,Object>>() {}); }
                catch (Exception ignore) {}
            }

            // ── Step 2: GitHub (real public API) ─────────────────────────
            Map<String, Object> ghData = null;
            String ghUsername = null;
            if (githubUrl != null && !githubUrl.isBlank()) {
                ghUsername = extractGithubUsername(githubUrl);
                if (ghUsername != null) {
                    ghData = fetchGithubData(ghUsername);
                }
            }

            // ── Step 3: Compute scores deterministically from LinkedIn data ─
            if (liData != null) {
                scoreFromLinkedIn(p, liData);
            }

            // ── Step 4: Groq generates narrative text only ────────────────
            String json = callGroq(linkedinUrl, ghUsername, ghData, p.getActivityDescription(), liData);
            populateNarrativeFromJson(p, json, ghData, liData == null);
            p.setAnalysisStatus("DONE");
            p.setLastRefreshedAt(LocalDateTime.now());

        } catch (Exception ex) {
            log.error("Profile analysis failed for {}: {}", studentId, ex.getMessage());
            // Provide safe defaults so radar chart still renders
            p.setLinkedinScore(50); p.setNetworkScore(40);
            p.setContentScore(30);  p.setGithubScore(0);
            p.setTechnicalScore(45);
            p.setProjectsScore(30); p.setSeminarsScore(25);
            p.setBootcampsScore(20); p.setResearchScore(15);
            p.setLinkedinSummary("Analysis temporarily unavailable. Showing estimated scores.");
            p.setGithubSummary(p.getGithubUrl() != null ? "GitHub analysis unavailable." : null);
            p.setStrengthsJson("[\"LinkedIn profile exists\"]");
            p.setGapsJson("[\"Full analysis could not be completed\"]");
            p.setRecommendationsJson("[\"Try refreshing the analysis\"]");
            p.setLastRefreshedAt(LocalDateTime.now());
            p.setAnalysisStatus("DONE");
        }

        return profileRepo.save(p);
    }

    // ── Daily auto-refresh (2 AM) ─────────────────────────────────────────
    @Scheduled(cron = "0 0 2 * * *")
    public void dailyRefresh() {
        log.info("Daily profile refresh starting…");
        profileRepo.findByAnalysisStatus("DONE").forEach(p -> {
            try {
                analyzeAndSave(p.getStudentId(), p.getLinkedinUrl(), p.getGithubUrl(), null);
            } catch (Exception e) {
                log.warn("Auto-refresh failed for {}: {}", p.getStudentId(), e.getMessage());
            }
        });
    }

    // ── GitHub public API ─────────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchGithubData(String username) {
        RestTemplate rt = new RestTemplate();
        HttpHeaders h = new HttpHeaders();
        h.set("User-Agent", "EduAdvisor-App");
        h.set("Accept", "application/vnd.github+json");
        HttpEntity<Void> req = new HttpEntity<>(h);

        Map<String, Object> result = new LinkedHashMap<>();
        try {
            ResponseEntity<Map> userResp = rt.exchange(
                "https://api.github.com/users/" + username,
                HttpMethod.GET, req, Map.class);
            Map<String, Object> user = userResp.getBody();
            if (user == null) return result;

            int repos    = toInt(user.get("public_repos"));
            int followers= toInt(user.get("followers"));
            String bio   = nvl(user.get("bio"));

            result.put("repos",     repos);
            result.put("followers", followers);
            result.put("bio",       bio);

            // Fetch repos for stars + language diversity
            ResponseEntity<List> repoResp = rt.exchange(
                "https://api.github.com/users/" + username + "/repos?per_page=50&sort=updated",
                HttpMethod.GET, req, List.class);
            List<Map<String, Object>> repoList = repoResp.getBody();
            if (repoList == null) repoList = List.of();

            int totalStars = 0;
            Set<String> langs = new LinkedHashSet<>();
            int recentCount = 0;
            LocalDateTime cutoff = LocalDateTime.now().minusYears(1);

            for (Map<String, Object> repo : repoList) {
                totalStars += toInt(repo.get("stargazers_count"));
                String lang = nvl(repo.get("language"));
                if (!lang.isEmpty()) langs.add(lang);
                String updatedAt = nvl(repo.get("updated_at"));
                if (!updatedAt.isEmpty()) {
                    try {
                        LocalDateTime upd = LocalDateTime.parse(updatedAt.replace("Z", ""));
                        if (upd.isAfter(cutoff)) recentCount++;
                    } catch (Exception ignore) {}
                }
            }

            result.put("stars",     totalStars);
            result.put("languages", String.join(", ", langs));
            result.put("langCount", langs.size());
            result.put("recentRepos", recentCount);
            result.put("totalRepos", repoList.size());

        } catch (Exception e) {
            log.warn("GitHub API error for {}: {}", username, e.getMessage());
        }
        return result;
    }

    // ── Deterministic scoring from real LinkedIn data ─────────────────────
    @SuppressWarnings({"unchecked","rawtypes"})
    private void scoreFromLinkedIn(StudentProfileLinks p, Map<String, Object> liData) {
        if (liData == null || liData.isEmpty()) return;

        List<Map<String,Object>> projects = liList(liData, "accomplishment_projects");
        List<Map<String,Object>> certs    = liList(liData, "certifications");
        List<Map<String,Object>> pubs     = liList(liData, "accomplishment_publications");
        List<Object>             actList  = rawList(liData, "activities");
        Object                   skillsRaw= liData.get("skills");
        int connections = toInt(liData.get("connections"));
        String headline = nvl(liData.get("headline"));
        String summary  = nvl(liData.get("summary"));
        List<Map<String,Object>> exps = liList(liData, "experiences");
        List<Map<String,Object>> edu  = liList(liData, "education");

        // Skills list
        List<String> skills = new ArrayList<>();
        if (skillsRaw instanceof List) {
            for (Object o : (List<?>)skillsRaw) {
                if (o instanceof String)         skills.add((String)o);
                else if (o instanceof Map)       { Object n = ((Map<?,?>)o).get("name"); if (n!=null) skills.add(n.toString()); }
            }
        }

        // ── projectsScore ─────────────────────────────────────────────────
        int nProj = projects.size();
        int projectsScore = nProj == 0 ? 0 : Math.min(95, 50 + nProj * 22);
        // 1=72, 2=94, 3=95

        // ── seminarsScore (certs with seminar/workshop/conference keywords) ─
        long nSem = certs.stream().filter(c -> {
            String t = (nvl(c.get("name")) + " " + nvl(c.get("authority"))).toLowerCase();
            return t.contains("seminar") || t.contains("workshop") || t.contains("conference")
                || t.contains("symposium") || t.contains("webinar") || t.contains("summit")
                || t.contains("forum") || t.contains("expo");
        }).count();
        int seminarsScore = nSem == 0 ? 0 : Math.min(92, 45 + (int)(nSem * 25));
        // 1=70, 2=95

        // ── bootcampsScore (certs with bootcamp/hackathon/intensive keywords) ─
        long nBoot = certs.stream().filter(c -> {
            String t = (nvl(c.get("name")) + " " + nvl(c.get("authority"))).toLowerCase();
            return t.contains("bootcamp") || t.contains("boot camp") || t.contains("hackathon")
                || t.contains("intensive") || t.contains("training") || t.contains("camp")
                || t.contains("program") || t.contains("certification");
        }).count();
        // Note: 'program' and 'certification' catch more cert names
        // Seminars already filtered above — try to avoid double-counting
        long nBootOnly = certs.stream().filter(c -> {
            String t = (nvl(c.get("name")) + " " + nvl(c.get("authority"))).toLowerCase();
            boolean isSem = t.contains("seminar") || t.contains("workshop") || t.contains("conference")
                         || t.contains("symposium") || t.contains("webinar") || t.contains("summit");
            boolean isBoot= t.contains("bootcamp") || t.contains("boot camp") || t.contains("hackathon")
                         || t.contains("intensive") || t.contains("training") || t.contains("camp");
            return isBoot && !isSem;
        }).count();
        // If we can distinguish, use that count; else fallback to total certs minus seminars
        long effectiveBoot = nBootOnly > 0 ? nBootOnly : Math.max(0, certs.size() - nSem);
        int bootcampsScore = effectiveBoot == 0 ? 0 : Math.min(92, 50 + (int)(effectiveBoot * 25));

        // ── researchScore (publications + research-related certs/honors) ───
        long nResearch = pubs.size();
        nResearch += certs.stream().filter(c -> {
            String t = (nvl(c.get("name")) + " " + nvl(c.get("authority"))).toLowerCase();
            return t.contains("research") || t.contains("publication") || t.contains("journal")
                || t.contains("poster") || t.contains("thesis") || t.contains("paper")
                || t.contains("icict") || t.contains("ieee") || t.contains("acm");
        }).count();
        int researchScore = nResearch == 0 ? 0 : Math.min(92, 45 + (int)(nResearch * 28));

        // ── networkScore (connections) ─────────────────────────────────────
        int networkScore = connections >= 500 ? 82
                         : connections >= 200 ? 68
                         : connections >= 100 ? 55
                         : connections >= 50  ? 42
                         : connections >= 20  ? 30
                         : 20;

        // ── contentScore (posts / activities) ─────────────────────────────
        int nPosts = actList.size();
        int contentScore = nPosts == 0 ? 10
                         : nPosts <= 3 ? 35
                         : nPosts <= 10 ? 55
                         : nPosts <= 25 ? 70
                         : 82;

        // ── linkedinScore (profile completeness) ──────────────────────────
        int complete = 0;
        if (!headline.isEmpty())    complete += 20;
        if (!summary.isEmpty())     complete += 25;
        if (!exps.isEmpty())        complete += 20;
        if (!edu.isEmpty())         complete += 15;
        if (skills.size() > 5)      complete += 10;
        if (!certs.isEmpty())       complete += 10;
        int linkedinScore = Math.min(100, complete);

        // ── technicalScore (technical skills count) ────────────────────────
        Set<String> techKw = Set.of("java","python","c++","c#","javascript","typescript","html","css",
            "react","angular","vue","node","spring","sql","mysql","postgresql","mongodb","firebase",
            "git","docker","kubernetes","aws","azure","gcp","machine learning","deep learning",
            "tensorflow","pytorch","android","ios","flutter","kotlin","php","ruby","rust","go",
            "bash","linux","rest","api","microservices","data science","opencv","numpy","pandas");
        long techCount = skills.stream()
            .filter(s -> techKw.stream().anyMatch(s.toLowerCase()::contains))
            .count();
        int technicalScore = techCount == 0 ? 20 : Math.min(100, (int)(15 + techCount * 8));

        p.setLinkedinScore(linkedinScore);
        p.setNetworkScore(networkScore);
        p.setContentScore(contentScore);
        p.setProjectsScore(projectsScore);
        p.setSeminarsScore(seminarsScore);
        p.setBootcampsScore(bootcampsScore);
        p.setResearchScore(researchScore);
        // technicalScore set here as base; GitHub may override/blend below
        p.setTechnicalScore(technicalScore);

        log.info("Deterministic scores for {} — proj:{} sem:{} boot:{} res:{} net:{} content:{} li:{} tech:{}",
            p.getStudentId(), projectsScore, seminarsScore, bootcampsScore, researchScore,
            networkScore, contentScore, linkedinScore, technicalScore);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> liList(Map<String,Object> m, String key) {
        Object v = m.get(key);
        if (!(v instanceof List)) return List.of();
        List<?> raw = (List<?>) v;
        List<Map<String,Object>> out = new ArrayList<>();
        for (Object o : raw) if (o instanceof Map) out.add((Map<String,Object>) o);
        return out;
    }
    @SuppressWarnings("unchecked")
    private List<Object> rawList(Map<String,Object> m, String key) {
        Object v = m.get(key);
        return v instanceof List ? (List<Object>) v : List.of();
    }

    // ── Groq: NARRATIVE ONLY (no score fields) — consistent with temperature=0 ─
    private String callGroq(String linkedinUrl, String ghUser, Map<String, Object> gh, String activityDescription, Map<String, Object> liData) {
        StringBuilder ghCtx = new StringBuilder();
        if (gh != null && !gh.isEmpty()) {
            ghCtx.append("GitHub: ").append(ghUser)
                 .append(" | Repos: ").append(gh.getOrDefault("repos", 0))
                 .append(" | Stars: ").append(gh.getOrDefault("stars", 0))
                 .append(" | Languages: ").append(gh.getOrDefault("languages", "?"))
                 .append(" | Followers: ").append(gh.getOrDefault("followers", 0)).append("\n");
        }

        String liCtx;
        if (liData != null && !liData.isEmpty()) {
            liCtx = linkedInDataService.buildProfileContext(liData);
        } else if (activityDescription != null && !activityDescription.isBlank()) {
            liCtx = "Student's self-reported activities:\n" + activityDescription;
        } else {
            liCtx = "LinkedIn URL: " + linkedinUrl;
        }

        String prompt =
            "You are a professional career advisor writing feedback for a university student.\n\n" +
            "=== Student's LinkedIn Profile ===\n" + liCtx + "\n" +
            (ghCtx.length() > 0 ? "=== GitHub ===\n" + ghCtx + "\n" : "") +
            "\nWrite a concise professional assessment. " +
            "Be specific — reference actual projects, certifications, and skills you see.\n" +
            "Return ONLY valid JSON (no markdown, no extra text):\n" +
            "{\n" +
            "  \"linkedinSummary\": \"2-3 sentence summary of the student's LinkedIn presence and strengths\",\n" +
            "  \"githubSummary\": \"1-2 sentence GitHub analysis, or null if no GitHub\",\n" +
            "  \"strengths\": [\"strength referencing specific project/cert/skill\", \"...\", \"...\"],\n" +
            "  \"gaps\": [\"specific gap 1\", \"specific gap 2\", \"specific gap 3\"],\n" +
            "  \"recommendations\": [\"actionable tip 1\", \"actionable tip 2\", \"actionable tip 3\"]\n" +
            "}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "llama-3.1-8b-instant");
        body.put("temperature", 0);   // deterministic narrative
        body.put("max_tokens", 700);
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        RestTemplate rt = new RestTemplate();
        ResponseEntity<Map> resp = rt.exchange(GROQ_URL, HttpMethod.POST, entity, Map.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.getBody().get("choices");
        @SuppressWarnings("unchecked")
        Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
        return msg.get("content").toString().trim();
    }

    // ── Populate narrative + GitHub computed score ────────────────────────
    private void populateNarrativeFromJson(StudentProfileLinks p, String json, Map<String, Object> ghData, boolean useGroqScores) {
        json = json.replaceAll("```json", "").replaceAll("```", "").trim();

        // When no LinkedIn data was fetched, fall back to Groq scores
        if (useGroqScores) {
            p.setLinkedinScore(  intField(json, "linkedinScore",  55));
            p.setNetworkScore(   intField(json, "networkScore",   45));
            p.setContentScore(   intField(json, "contentScore",   35));
            p.setProjectsScore(  intField(json, "projectsScore",  30));
            p.setSeminarsScore(  intField(json, "seminarsScore",  25));
            p.setBootcampsScore( intField(json, "bootcampsScore", 20));
            p.setResearchScore(  intField(json, "researchScore",  15));
            p.setGithubScore(    intField(json, "githubScore",     0));
            p.setTechnicalScore( intField(json, "technicalScore", 45));
        }

        p.setLinkedinSummary(strField(json, "linkedinSummary", "Profile analyzed."));
        p.setGithubSummary(  strField(json, "githubSummary",   null));

        // GitHub score — always computed from real API data
        if (ghData != null && !ghData.isEmpty()) {
            int repos    = toInt(ghData.get("repos"));
            int stars    = toInt(ghData.get("stars"));
            int langCnt  = toInt(ghData.get("langCount"));
            int followers= toInt(ghData.get("followers"));
            int recent   = toInt(ghData.get("recentRepos"));
            int totalR   = Math.max(1, toInt(ghData.get("totalRepos")));

            int repoSc  = Math.min(100, repos    * 5);
            int starSc  = Math.min(100, stars    * 5);
            int langSc  = Math.min(100, langCnt  * 20);
            int follSc  = Math.min(100, followers* 4);
            int actSc   = Math.min(100, recent * 100 / totalR);
            int ghScore = (repoSc + starSc + langSc + follSc + actSc) / 5;
            p.setGithubScore(ghScore);

            int techFromGh = (langSc * 2 + starSc + repoSc) / 4;
            int blended    = (p.getTechnicalScore() + techFromGh) / 2;
            p.setTechnicalScore(Math.min(100, blended));

            p.setGithubRepos(repos);
            p.setGithubStars(stars);
            p.setGithubFollowers(followers);
            p.setGithubLanguages(nvl(ghData.get("languages")));
        }

        // JSON array fields
        p.setStrengthsJson(     arrayField(json, "strengths"));
        p.setGapsJson(          arrayField(json, "gaps"));
        p.setRecommendationsJson(arrayField(json, "recommendations"));
    }

    // ── Field extractors ──────────────────────────────────────────────────
    private int intField(String json, String key, int def) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : def;
    }

    private String strField(String json, String key, String def) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : def;
    }

    private String arrayField(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\[[^\\]]*\\])", Pattern.DOTALL);
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : "[]";
    }

    private int toInt(Object v) {
        if (v == null) return 0;
        try { return ((Number) v).intValue(); } catch (Exception e) { return 0; }
    }

    private String nvl(Object v) { return v != null ? v.toString() : ""; }

    // ── Username extractor ────────────────────────────────────────────────
    private String extractGithubUsername(String url) {
        url = url.trim().replaceAll("/$", "");
        String[] parts = url.split("/");
        String u = parts[parts.length - 1];
        return u.isEmpty() ? null : u;
    }
}
