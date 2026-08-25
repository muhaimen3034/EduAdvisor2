package com.example.eduadvisor2.service;

import com.example.eduadvisor2.model.Discussion;
import com.example.eduadvisor2.model.ExamPaper;
import com.example.eduadvisor2.model.LostFoundReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    public void notifyAnswered(Discussion discussion) {
        String to = discussion.getStudentEmail();
        if (to == null || to.isBlank()) {
            log.warn("No notification email recorded for student {} — skipping email", discussion.getStudentId());
            return;
        }
        String subject = "EduAdvisor — " + discussion.getTeacherName() + " answered your question";
        String body = """
                Hi %s,

                Your teacher has answered your question on EduAdvisor!

                Teacher  : %s
                ─────────────────────────────────────────────────────
                Your Question:
                %s

                Teacher's Reply:
                %s
                ─────────────────────────────────────────────────────

                Log in to EduAdvisor to view the full thread with any attached images.

                — EduAdvisor
                """.formatted(
                discussion.getStudentName(),
                discussion.getTeacherName(),
                truncate(discussion.getQuestion(), 400),
                truncate(discussion.getAnswer(),   400));
        sendEmail(to, subject, body);
    }

    public void notifyBookingConfirmed(String studentName, String studentEmail,
                                       String teacherName, String slot, String reason) {
        if (studentEmail == null || studentEmail.isBlank()) {
            log.warn("No email for student — skipping booking confirmation");
            return;
        }
        String reasonLine = (reason != null && !reason.isBlank()) ? "Reason    : " + reason + "\n" : "";
        String subject = "EduAdvisor — Counselling Session Booked";
        String body = """
                Hi %s,

                Your counselling session has been booked successfully!

                ─────────────────────────────────────────────────────
                Teacher   : %s
                Time Slot : %s
                %s
                ─────────────────────────────────────────────────────

                Please arrive 5 minutes early for your session.
                Log in to EduAdvisor to view or cancel your booking.

                — EduAdvisor
                """.formatted(studentName, teacherName, slot, reasonLine);
        sendEmail(studentEmail, subject, body);
    }

    public void notifyLostItemFound(LostFoundReport report) {
        String to = report.getStudentEmail();
        if (to == null || to.isBlank()) {
            log.warn("No email for student {} — skipping lost & found notification", report.getStudentId());
            return;
        }
        String subject = "EduAdvisor — Great news! Your lost item has been found 🎉";
        String noteSection = (report.getCoordinatorNote() != null && !report.getCoordinatorNote().isBlank())
                ? "\nCoordinator's Note:\n" + report.getCoordinatorNote() + "\n"
                : "";
        String body = """
                Hi %s,

                Great news! The campus coordinator has located your lost item.

                ─────────────────────────────────────────────────────
                Location Reported : %s
                Item Description  : %s
                %s
                ─────────────────────────────────────────────────────

                Please visit the campus Lost & Found office to collect your item.
                Log in to EduAdvisor for full details.

                — EduAdvisor
                """.formatted(
                report.getStudentName(),
                report.getLocation(),
                truncate(report.getDescription(), 300),
                noteSection);
        sendEmail(to, subject, body);
    }

    public void notifyPaperSubmitted(ExamPaper paper) {
        String to = paper.getStudentEmail();
        if (to == null || to.isBlank()) {
            log.warn("No email for student {} — skipping paper submission notification", paper.getStudentId());
            return;
        }
        String subject = "EduAdvisor — " + paper.getTeacherName() + " submitted your " + paper.getType() + " paper";
        String body = """
                Hi %s,

                Your teacher has submitted your %s paper on EduAdvisor.

                Teacher  : %s
                Title    : %s
                Type     : %s
                ─────────────────────────────────────────────────────

                Log in to EduAdvisor and go to "My Papers" in the sidebar to view the submitted images.

                — EduAdvisor
                """.formatted(
                paper.getStudentName(),
                paper.getType(),
                paper.getTeacherName(),
                paper.getTitle(),
                paper.getType());
        sendEmail(to, subject, body);
    }

    private void sendEmail(String to, String subject, String body) {
        if (mailSender == null || mailFrom.isBlank()) {
            log.warn("══════════════════════════════════════════════════");
            log.warn("EMAIL (mail not configured)");
            log.warn("  Would send to : {}", to);
            log.warn("  Subject       : {}", subject);
            log.warn("══════════════════════════════════════════════════");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(mailFrom);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("✅ Email sent to {}", to);
        } catch (Exception e) {
            log.error("❌ Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
