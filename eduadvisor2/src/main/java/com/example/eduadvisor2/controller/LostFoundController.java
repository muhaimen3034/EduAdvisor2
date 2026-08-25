package com.example.eduadvisor2.controller;

import com.example.eduadvisor2.model.Booking;
import com.example.eduadvisor2.model.Course;
import com.example.eduadvisor2.model.LostFoundReport;
import com.example.eduadvisor2.model.Student;
import com.example.eduadvisor2.model.Teacher;
import com.example.eduadvisor2.repository.BookingRepository;
import com.example.eduadvisor2.repository.CourseRepository;
import com.example.eduadvisor2.repository.EnrollmentRepository;
import com.example.eduadvisor2.repository.LostFoundRepository;
import com.example.eduadvisor2.repository.TeacherRepository;
import com.example.eduadvisor2.service.NotificationService;
import com.example.eduadvisor2.service.StudentService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.data.domain.Sort;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class LostFoundController {

    private final LostFoundRepository lostFoundRepository;
    private final StudentService       studentService;
    private final NotificationService  notificationService;
    private final CourseRepository     courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TeacherRepository    teacherRepository;
    private final BookingRepository    bookingRepository;

    private static final List<String> LOCATIONS = List.of(
            "Main Building Lobby",
            "Library",
            "Cafeteria / Food Court",
            "Computer Lab (Ground Floor)",
            "Computer Lab (2nd Floor)",
            "Science & Engineering Block",
            "Business Faculty Block",
            "Arts & Humanities Block",
            "Auditorium",
            "Sports Ground",
            "Parking Area",
            "Boys' Hostel",
            "Girls' Hostel",
            "Admin Office",
            "Prayer Room / Mosque",
            "Health Center",
            "Classroom Wing A (101–200)",
            "Classroom Wing B (201–300)",
            "Classroom Wing C (301–400)"
    );

    @GetMapping("/lost-found.html")
    public String lostFound(HttpSession session, Model model) {
        if (session.getAttribute("studentId") == null) return "redirect:/";
        String sid = (String) session.getAttribute("studentId");

        model.addAttribute("studentName", session.getAttribute("studentName"));
        model.addAttribute("studentId", sid);
        model.addAttribute("locations", LOCATIONS);

        studentService.findByStudentId(sid).ifPresent(s -> {
            String email = (s.getNotifEmail() != null && !s.getNotifEmail().isBlank())
                    ? s.getNotifEmail() : s.getEmail();
            model.addAttribute("studentEmail", email != null ? email : "");
        });

        model.addAttribute("reports", lostFoundRepository.findByStudentIdOrderByReportedAtDesc(sid));
        return "lost-found";
    }

    @GetMapping("/coordinator-dashboard.html")
    public String coordinatorDashboard(HttpSession session, Model model) {
        if (session.getAttribute("coordinatorId") == null) return "redirect:/";
        model.addAttribute("coordinatorName", session.getAttribute("coordinatorName"));
        List<LostFoundReport> reports = lostFoundRepository.findAll(
                Sort.by(Sort.Direction.DESC, "reportedAt"));
        model.addAttribute("reports", reports);
        model.addAttribute("totalCount",   reports.size());
        model.addAttribute("pendingCount", reports.stream().filter(r -> r.getStatus() == null || "PENDING".equals(r.getStatus())).count());
        model.addAttribute("waitingCount", reports.stream().filter(r -> "WAITING".equals(r.getStatus())).count());
        model.addAttribute("foundCount",   reports.stream().filter(r -> "FOUND".equals(r.getStatus())).count());

        // Course overview data
        List<Course> courses = courseRepository.findAll(Sort.by("code"));
        List<Map<String, Object>> courseStats = new ArrayList<>();
        for (Course course : courses) {
            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("course", course);
            stat.put("formattedDays", formatDays(course.getDays()));

            Teacher teacher = teacherRepository.findByName(course.getInstructor()).orElse(null);
            stat.put("teacher", teacher);

            List<Student> students = enrollmentRepository.findByCourse_Code(course.getCode()).stream()
                    .map(e -> studentService.findByStudentId(e.getStudentId()))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
            stat.put("students", students);
            stat.put("studentCount", students.size());
            courseStats.add(stat);
        }
        model.addAttribute("courseStats", courseStats);

        // Counselling bookings
        List<Booking> allBookings = bookingRepository.findAll(
                Sort.by(Sort.Direction.DESC, "bookedAt"));
        List<Map<String, Object>> bookingData = new ArrayList<>();
        for (Booking b : allBookings) {
            Map<String, Object> bmap = new LinkedHashMap<>();
            bmap.put("booking", b);
            bmap.put("studentName", studentService.findByStudentId(b.getStudentId())
                    .map(Student::getName).orElse("Unknown"));
            bookingData.add(bmap);
        }
        model.addAttribute("bookingData",  bookingData);
        model.addAttribute("bookingCount", allBookings.size());

        return "coordinator-dashboard";
    }

    @PostMapping("/lost-found/report")
    public String submitReport(
            @RequestParam String studentName,
            @RequestParam String studentEmail,
            @RequestParam String location,
            @RequestParam String description,
            @RequestParam(required = false) MultipartFile image,
            HttpSession session) throws IOException {

        String studentId = (String) session.getAttribute("studentId");
        if (studentId == null) return "redirect:/";

        LostFoundReport.LostFoundReportBuilder b = LostFoundReport.builder()
                .studentId(studentId)
                .studentName(studentName.trim())
                .studentEmail(studentEmail.trim())
                .location(location)
                .description(description.trim())
                .status("PENDING")
                .reportedAt(LocalDateTime.now());

        if (image != null && !image.isEmpty()) {
            b.imageBase64(Base64.getEncoder().encodeToString(image.getBytes()));
            b.imageType(image.getContentType());
        }

        lostFoundRepository.save(b.build());
        return "redirect:/lost-found.html?submitted=true";
    }

    @PostMapping("/lost-found/status/{id}")
    public String updateStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String coordinatorNote,
            HttpSession session) {

        if (session.getAttribute("coordinatorId") == null) return "redirect:/";

        LostFoundReport report = lostFoundRepository.findById(id).orElse(null);
        if (report == null) return "redirect:/coordinator-dashboard.html";

        report.setStatus(status);
        report.setCoordinatorNote(coordinatorNote != null ? coordinatorNote.strip() : null);
        report.setUpdatedAt(LocalDateTime.now());
        lostFoundRepository.save(report);

        if ("FOUND".equals(status)) {
            notificationService.notifyLostItemFound(report);
        }

        return "redirect:/coordinator-dashboard.html";
    }

    private static final String[] DAY_NAMES = {"", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    private String formatDays(String days) {
        if (days == null || days.isBlank()) return "—";
        StringBuilder sb = new StringBuilder();
        for (String part : days.split(",")) {
            try {
                int idx = Integer.parseInt(part.trim());
                if (idx >= 1 && idx <= 7) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(DAY_NAMES[idx]);
                }
            } catch (NumberFormatException ignored) {}
        }
        return sb.isEmpty() ? days : sb.toString();
    }
}
