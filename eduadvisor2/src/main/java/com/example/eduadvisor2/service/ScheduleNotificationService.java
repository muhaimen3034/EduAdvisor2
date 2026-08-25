package com.example.eduadvisor2.service;

import com.example.eduadvisor2.model.ScheduleEvent;
import com.example.eduadvisor2.model.Student;
import com.example.eduadvisor2.repository.ScheduleEventRepository;
import com.example.eduadvisor2.repository.StudentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class ScheduleNotificationService {

    @Autowired
    private ScheduleEventRepository eventRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Scheduled(cron = "0 0 8 * * *")
    public void sendUpcomingEventReminders() {
        String tomorrow = LocalDate.now().plusDays(1)
                .format(DateTimeFormatter.ISO_LOCAL_DATE);

        List<ScheduleEvent> upcoming = eventRepository.findByDateAndNotifiedFalse(tomorrow);
        log.info("Schedule reminder check — {} event(s) due tomorrow ({})", upcoming.size(), tomorrow);

        for (ScheduleEvent event : upcoming) {
            Optional<Student> opt = studentRepository.findByStudentId(event.getStudentId());
            if (opt.isEmpty()) {
                markNotified(event);
                continue;
            }

            Student student = opt.get();
            String to = (student.getNotifEmail() != null && !student.getNotifEmail().isBlank())
                    ? student.getNotifEmail()
                    : student.getEmail();

            if (to == null || to.isBlank()) {
                log.warn("No email for student {} — skipping event '{}'", student.getStudentId(), event.getTitle());
                markNotified(event);
                continue;
            }

            sendReminder(to, student.getName(), event, tomorrow);
            markNotified(event);
        }
    }

    private void sendReminder(String to, String studentName, ScheduleEvent event, String isoDate) {
        String displayDate;
        try {
            displayDate = LocalDate.parse(isoDate)
                    .format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy"));
        } catch (Exception e) {
            displayDate = isoDate;
        }

        String timeLabel = (event.getTime() != null && !event.getTime().isBlank())
                ? event.getTime()
                : "All day";

        String typeLine = (event.getType() != null && !event.getType().isBlank())
                ? "\nType     : " + capitalize(event.getType())
                : "";

        String subject = "EduAdvisor Reminder — “" + event.getTitle() + "” is tomorrow";
        String body = """
                Hi %s,

                This is a friendly reminder that you have an upcoming event tomorrow!

                ─────────────────────────────────────────────────────
                Event    : %s
                Date     : %s (Tomorrow)
                Time     : %s%s
                ─────────────────────────────────────────────────────

                Log in to EduAdvisor and check your Schedule page for full details.

                — EduAdvisor
                """.formatted(studentName, event.getTitle(), displayDate, timeLabel, typeLine);

        if (mailSender == null || mailFrom.isBlank()) {
            log.warn("══ EMAIL (mail not configured) — Would send reminder to {}", to);
            log.warn("   Subject : {}", subject);
            return;
        }

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(mailFrom);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("✅ Schedule reminder sent to {} for event '{}'", to, event.getTitle());
        } catch (Exception e) {
            log.error("❌ Failed to send schedule reminder to {}: {}", to, e.getMessage());
        }
    }

    private void markNotified(ScheduleEvent event) {
        event.setNotified(true);
        eventRepository.save(event);
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}
