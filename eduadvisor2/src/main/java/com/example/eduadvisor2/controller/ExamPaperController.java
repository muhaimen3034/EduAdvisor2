package com.example.eduadvisor2.controller;

import com.example.eduadvisor2.model.ExamPaper;
import com.example.eduadvisor2.model.ExamPaperImage;
import com.example.eduadvisor2.model.Student;
import com.example.eduadvisor2.repository.CourseRepository;
import com.example.eduadvisor2.repository.EnrollmentRepository;
import com.example.eduadvisor2.repository.ExamPaperImageRepository;
import com.example.eduadvisor2.repository.ExamPaperRepository;
import com.example.eduadvisor2.service.NotificationService;
import com.example.eduadvisor2.service.StudentService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ExamPaperController {

    private final ExamPaperRepository      examPaperRepository;
    private final ExamPaperImageRepository examPaperImageRepository;
    private final CourseRepository         courseRepository;
    private final EnrollmentRepository     enrollmentRepository;
    private final StudentService           studentService;
    private final NotificationService      notificationService;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${groq.api.key:}")
    private String groqApiKey;

    private static final String GROQ_URL   = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.1-8b-instant";

    @GetMapping("/teacher-papers.html")
    public String teacherPapersPage(HttpSession session, Model model) {
        if (session.getAttribute("teacherId") == null) return "redirect:/";
        String teacherId   = (String) session.getAttribute("teacherId");
        String teacherName = (String) session.getAttribute("teacherName");
        model.addAttribute("teacherId", teacherId);
        model.addAttribute("teacherName", teacherName);

        List<Student> students = courseRepository.findByInstructor(teacherName).stream()
                .flatMap(c -> enrollmentRepository.findByCourse_Code(c.getCode()).stream())
                .map(e -> studentService.findByStudentId(e.getStudentId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .distinct()
                .collect(Collectors.toList());
        model.addAttribute("students", students);

        model.addAttribute("submittedPapers",
                examPaperRepository.findByTeacherIdOrderBySubmittedAtDesc(teacherId));

        return "teacher-papers";
    }

    @PostMapping("/papers/submit")
    public String submitPaper(
            @RequestParam String studentId,
            @RequestParam String title,
            @RequestParam String type,
            @RequestParam(value = "images", required = false) List<MultipartFile> images,
            HttpSession session) throws IOException {

        String teacherId   = (String) session.getAttribute("teacherId");
        String teacherName = (String) session.getAttribute("teacherName");
        if (teacherId == null) return "redirect:/";

        Student student = studentService.findByStudentId(studentId).orElse(null);
        if (student == null || title.isBlank()) return "redirect:/teacher-papers.html?error=true";

        String studentEmail = (student.getNotifEmail() != null && !student.getNotifEmail().isBlank())
                ? student.getNotifEmail() : student.getEmail();

        List<ExamPaperImage> imageList = new ArrayList<>();
        if (images != null) {
            for (MultipartFile file : images) {
                if (!file.isEmpty()) {
                    imageList.add(ExamPaperImage.builder()
                            .imageBase64(Base64.getEncoder().encodeToString(file.getBytes()))
                            .imageType(file.getContentType() != null ? file.getContentType() : "image/jpeg")
                            .build());
                }
            }
        }

        ExamPaper paper = ExamPaper.builder()
                .teacherId(teacherId)
                .teacherName(teacherName)
                .studentId(studentId)
                .studentName(student.getName())
                .studentEmail(studentEmail)
                .title(title.trim())
                .type(type)
                .images(imageList)
                .submittedAt(LocalDateTime.now())
                .build();

        examPaperRepository.save(paper);
        notificationService.notifyPaperSubmitted(paper);

        return "redirect:/teacher-papers.html?success=true";
    }

    @PostMapping("/papers/delete/{id}")
    public String deletePaper(@PathVariable Long id, HttpSession session) {
        String teacherId = (String) session.getAttribute("teacherId");
        if (teacherId == null) return "redirect:/";
        examPaperRepository.findById(id).ifPresent(paper -> {
            if (teacherId.equals(paper.getTeacherId())) {
                examPaperRepository.delete(paper);
            }
        });
        return "redirect:/teacher-papers.html?deleted=true";
    }

    @PostMapping("/papers/analyse/{paperId}")
    @ResponseBody
    public Map<String, String> analysePaper(@PathVariable Long paperId, HttpSession session) {
        if (session.getAttribute("studentId") == null) return Map.of("error", "Not logged in.");
        if (groqApiKey == null || groqApiKey.isBlank()) return Map.of("error", "AI not configured.");

        ExamPaper paper = examPaperRepository.findById(paperId).orElse(null);
        if (paper == null) return Map.of("error", "Paper not found.");

        String studentId = (String) session.getAttribute("studentId");
        if (!studentId.equals(paper.getStudentId())) return Map.of("error", "Access denied.");

        String prompt =
            "You are an academic advisor. A teacher submitted an exam paper to a student with the following details:\n" +
            "- Paper type: " + paper.getType() + "\n" +
            "- Title / Teacher feedback: \"" + paper.getTitle() + "\"\n" +
            "- Submitted by: " + paper.getTeacherName() + "\n\n" +
            "Analyse this feedback and help the student understand:\n" +
            "1. What the teacher is saying about their performance\n" +
            "2. What specific areas need improvement\n" +
            "3. 2-3 concrete action steps to improve\n\n" +
            "Be concise. Use bullet points. Address the student directly.";

        try {
            List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content",
                    "You are a helpful academic advisor. Give concise, student-friendly analysis using bullet points. No long paragraphs."),
                Map.of("role", "user", "content", prompt)
            );
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", GROQ_MODEL);
            body.put("messages", messages);
            body.put("max_tokens", 512);
            body.put("temperature", 0.5);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(groqApiKey);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                GROQ_URL, HttpMethod.POST, new HttpEntity<>(body, headers),
                (Class<Map<String, Object>>) (Class<?>) Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.getBody().get("choices");
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            return Map.of("analysis", (String) msg.get("content"));
        } catch (Exception e) {
            log.error("Paper analysis error", e);
            return Map.of("error", "Analysis failed. Please try again.");
        }
    }

    /** Serves a single exam paper image by its ID — avoids embedding huge base64 in HTML. */
    @GetMapping("/papers/image/{id}")
    @ResponseBody
    public ResponseEntity<byte[]> getImage(@PathVariable Long id, HttpSession session) {
        if (session.getAttribute("studentId") == null && session.getAttribute("teacherId") == null) {
            return ResponseEntity.status(403).build();
        }
        return examPaperImageRepository.findById(id)
                .map(img -> {
                    byte[] bytes = Base64.getDecoder().decode(img.getImageBase64());
                    String ct = img.getImageType() != null ? img.getImageType() : "image/jpeg";
                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(ct))
                            .body(bytes);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/my-papers.html")
    public String myPapersPage(HttpSession session, Model model) {
        if (session.getAttribute("studentId") == null) return "redirect:/";
        String studentId = (String) session.getAttribute("studentId");
        model.addAttribute("studentName", session.getAttribute("studentName"));
        model.addAttribute("studentId", studentId);
        model.addAttribute("papers", examPaperRepository.findByStudentIdOrderBySubmittedAtDesc(studentId));
        return "my-papers";
    }
}
