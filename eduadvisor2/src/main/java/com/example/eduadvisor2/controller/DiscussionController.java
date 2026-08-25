package com.example.eduadvisor2.controller;

import com.example.eduadvisor2.model.Discussion;
import com.example.eduadvisor2.repository.DiscussionRepository;
import com.example.eduadvisor2.repository.TeacherRepository;
import com.example.eduadvisor2.service.NotificationService;
import com.example.eduadvisor2.service.StudentService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;

@Controller
@RequiredArgsConstructor
public class DiscussionController {

    private final DiscussionRepository discussionRepository;
    private final TeacherRepository    teacherRepository;
    private final StudentService       studentService;
    private final NotificationService  notificationService;

    @PostMapping("/discussion/ask")
    public String askQuestion(
            @RequestParam String teacherId,
            @RequestParam String question,
            @RequestParam(required = false) MultipartFile image,
            HttpSession session) throws IOException {

        String studentId   = (String) session.getAttribute("studentId");
        String studentName = (String) session.getAttribute("studentName");
        if (studentId == null) return "redirect:/";

        var teacher = teacherRepository.findByTeacherId(teacherId).orElse(null);
        if (teacher == null || question.isBlank()) return "redirect:/discussion.html";

        String studentEmail = studentService.findByStudentId(studentId)
                .map(s -> (s.getNotifEmail() != null && !s.getNotifEmail().isBlank())
                        ? s.getNotifEmail() : s.getEmail())
                .orElse(null);

        Discussion.DiscussionBuilder b = Discussion.builder()
                .studentId(studentId)
                .studentName(studentName)
                .studentEmail(studentEmail)
                .teacherId(teacherId)
                .teacherName(teacher.getName())
                .question(question.trim())
                .askedAt(LocalDateTime.now());

        if (image != null && !image.isEmpty()) {
            b.questionImageBase64(Base64.getEncoder().encodeToString(image.getBytes()));
            b.questionImageType(image.getContentType());
        }

        discussionRepository.save(b.build());
        return "redirect:/discussion.html";
    }

    @PostMapping("/discussion/answer/{id}")
    public String answerQuestion(
            @PathVariable Long id,
            @RequestParam String answer,
            @RequestParam(required = false) MultipartFile image,
            HttpSession session) throws IOException {

        String teacherId = (String) session.getAttribute("teacherId");
        if (teacherId == null) return "redirect:/";

        Discussion d = discussionRepository.findById(id).orElse(null);
        if (d == null || !d.getTeacherId().equals(teacherId)) return "redirect:/teacher-discussions.html";

        d.setAnswer(answer.trim());
        d.setAnsweredAt(LocalDateTime.now());

        if (image != null && !image.isEmpty()) {
            d.setAnswerImageBase64(Base64.getEncoder().encodeToString(image.getBytes()));
            d.setAnswerImageType(image.getContentType());
        }

        discussionRepository.save(d);

        notificationService.notifyAnswered(d);

        return "redirect:/teacher-discussions.html";
    }
}
