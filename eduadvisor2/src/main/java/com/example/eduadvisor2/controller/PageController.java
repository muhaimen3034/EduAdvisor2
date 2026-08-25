package com.example.eduadvisor2.controller;

import com.example.eduadvisor2.model.Booking;
import com.example.eduadvisor2.model.Course;
import com.example.eduadvisor2.model.Student;
import com.example.eduadvisor2.repository.BookingRepository;
import com.example.eduadvisor2.repository.CourseRepository;
import com.example.eduadvisor2.repository.DiscussionRepository;
import com.example.eduadvisor2.repository.EnrollmentRepository;
import com.example.eduadvisor2.repository.TeacherRepository;
import com.example.eduadvisor2.service.NotificationService;
import com.example.eduadvisor2.service.StudentService;
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class PageController {

    private final StudentService        studentService;
    private final CourseRepository      courseRepository;
    private final EnrollmentRepository  enrollmentRepository;
    private final TeacherRepository     teacherRepository;
    private final DiscussionRepository  discussionRepository;
    private final BookingRepository     bookingRepository;
    private final NotificationService   notificationService;

    private boolean isLoggedIn(HttpSession session) {
        return session.getAttribute("studentId") != null;
    }

    private boolean isTeacherLoggedIn(HttpSession session) {
        return session.getAttribute("teacherId") != null;
    }

    private void addStudentAttributes(HttpSession session, Model model) {
        model.addAttribute("studentName", session.getAttribute("studentName"));
        model.addAttribute("studentId", session.getAttribute("studentId"));
    }

    @GetMapping("/teacher-dashboard.html")
    public String teacherDashboard(HttpSession session, Model model) {
        if (!isTeacherLoggedIn(session)) return "redirect:/";
        String name = (String) session.getAttribute("teacherName");
        String tid  = (String) session.getAttribute("teacherId");
        model.addAttribute("teacherName", name);
        model.addAttribute("teacherId", tid);

        boolean isCoordinator = teacherRepository.findByTeacherId(tid)
                .map(t -> t.isCoordinator()).orElse(false);
        model.addAttribute("isCoordinator", isCoordinator);

        List<Course> courses = courseRepository.findByInstructor(name);
        model.addAttribute("courses", courses);

        Map<String, List<Student>> enrolledStudents = new LinkedHashMap<>();
        for (Course c : courses) {
            List<Student> students = enrollmentRepository.findByCourse_Code(c.getCode()).stream()
                    .map(e -> studentService.findByStudentId(e.getStudentId()))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
            enrolledStudents.put(c.getCode(), students);
        }
        model.addAttribute("enrolledStudents", enrolledStudents);
        model.addAttribute("totalStudents",
                enrolledStudents.values().stream().mapToLong(List::size).sum());

        List<Booking> myBookings = bookingRepository.findByTeacherNameOrderByBookedAtDesc(name);
        List<Map<String, Object>> bookingData = new java.util.ArrayList<>();
        for (Booking b : myBookings) {
            Map<String, Object> bmap = new LinkedHashMap<>();
            bmap.put("booking", b);
            bmap.put("studentName", studentService.findByStudentId(b.getStudentId())
                    .map(Student::getName).orElse("Unknown"));
            bookingData.add(bmap);
        }
        model.addAttribute("bookingData", bookingData);
        model.addAttribute("bookingCount", myBookings.size());

        String advisorBatch = (String) session.getAttribute("advisorBatch");
        boolean isAdvisor = advisorBatch != null;
        model.addAttribute("isAdvisor", isAdvisor);
        model.addAttribute("advisorBatch", advisorBatch != null ? advisorBatch : "");

        List<Map<String, Object>> advisorStudents = new java.util.ArrayList<>();
        if (isAdvisor) {
            for (Student s : studentService.findByBatch(advisorBatch)) {
                Map<String, Object> sd = new LinkedHashMap<>();
                sd.put("student", s);
                sd.put("courseCount", enrollmentRepository.findByStudentId(s.getStudentId()).size());
                double gpa = s.getGpa() != null ? s.getGpa() : 0.0;
                sd.put("level", gpa >= 3.5 ? "STRONG" : gpa >= 2.5 ? "AVERAGE" : "WEAK");
                advisorStudents.add(sd);
            }
        }
        model.addAttribute("advisorStudents", advisorStudents);
        model.addAttribute("advStrongCount", advisorStudents.stream().filter(sd -> "STRONG".equals(sd.get("level"))).count());
        model.addAttribute("advAvgCount",    advisorStudents.stream().filter(sd -> "AVERAGE".equals(sd.get("level"))).count());
        model.addAttribute("advWeakCount",   advisorStudents.stream().filter(sd -> "WEAK".equals(sd.get("level"))).count());

        return "teacher-dashboard";
    }

    @GetMapping("/dashboard.html")
    public String dashboard(HttpSession session, Model model) {
        if (!isLoggedIn(session)) return "redirect:/";
        addStudentAttributes(session, model);
        String sid = (String) session.getAttribute("studentId");
        studentService.findByStudentId(sid).ifPresent(s -> {
            model.addAttribute("gpa", s.getGpa());
            model.addAttribute("credits", s.getCreditsCompleted());
            model.addAttribute("year", s.getAcademicYear());
            model.addAttribute("program", s.getProgram());
            model.addAttribute("email", s.getEmail());
        });
        return "dashboard";
    }

    @GetMapping("/advice.html")
    public String advice(HttpSession session, Model model) {
        if (!isLoggedIn(session)) return "redirect:/";
        addStudentAttributes(session, model);
        return "advice";
    }

    @GetMapping("/counselling.html")
    public String counselling(HttpSession session, Model model,
                              @RequestParam(required = false) String booked) {
        if (!isLoggedIn(session)) return "redirect:/";
        addStudentAttributes(session, model);
        String sid = (String) session.getAttribute("studentId");
        model.addAttribute("bookings", bookingRepository.findByStudentIdOrderByBookedAtDesc(sid));
        model.addAttribute("bookedSuccess", "1".equals(booked));
        return "counselling";
    }

    @PostMapping("/counselling/book")
    public String bookCounselling(@RequestParam String teacherName,
                                  @RequestParam String slot,
                                  @RequestParam(required = false) String reason,
                                  HttpSession session) {
        if (!isLoggedIn(session)) return "redirect:/";
        String sid = (String) session.getAttribute("studentId");
        Booking booking = Booking.builder()
                .studentId(sid)
                .teacherName(teacherName)
                .slot(slot)
                .reason(reason != null ? reason.strip() : null)
                .bookedAt(java.time.LocalDateTime.now())
                .build();
        bookingRepository.save(booking);
        studentService.findByStudentId(sid).ifPresent(s -> {
            String email = (s.getNotifEmail() != null && !s.getNotifEmail().isBlank())
                    ? s.getNotifEmail() : s.getEmail();
            notificationService.notifyBookingConfirmed(s.getName(), email, teacherName, slot, reason);
        });
        return "redirect:/counselling.html?booked=1";
    }

    @PostMapping("/counselling/cancel/{id}")
    public String cancelCounselling(@PathVariable Long id, HttpSession session) {
        if (!isLoggedIn(session)) return "redirect:/";
        bookingRepository.deleteById(id);
        return "redirect:/counselling.html";
    }

    @GetMapping("/schedule.html")
    public String schedule(HttpSession session, Model model) {
        if (!isLoggedIn(session)) return "redirect:/";
        addStudentAttributes(session, model);
        return "schedule";
    }

    @GetMapping("/teachers.html")
    public String teachers(HttpSession session, Model model) {
        if (!isLoggedIn(session)) return "redirect:/";
        addStudentAttributes(session, model);
        return "teachers";
    }

    @GetMapping("/resources.html")
    public String resources(HttpSession session, Model model) {
        if (!isLoggedIn(session)) return "redirect:/";
        addStudentAttributes(session, model);
        return "resources";
    }

    @GetMapping("/profile.html")
    public String profile(HttpSession session, Model model) {
        if (!isLoggedIn(session)) return "redirect:/";
        addStudentAttributes(session, model);
        return "profile";
    }

    @GetMapping("/CreaditTransfer.html")
    public String creditTransfer(HttpSession session, Model model) {
        if (!isLoggedIn(session)) return "redirect:/";
        addStudentAttributes(session, model);
        return "CreaditTransfer";
    }

    @GetMapping("/my-courses.html")
    public String myCourses(HttpSession session, Model model) {
        if (!isLoggedIn(session)) return "redirect:/";
        addStudentAttributes(session, model);
        String bg = (String) session.getAttribute("batchGroup");
        model.addAttribute("batchGroup", bg != null ? bg : "");
        return "my-courses";
    }

    @GetMapping("/study-coach.html")
    public String studyCoach(HttpSession session, Model model) {
        if (!isLoggedIn(session)) return "redirect:/";
        addStudentAttributes(session, model);
        return "study-coach";
    }

    @GetMapping("/learning-gap.html")
    public String learningGap(HttpSession session, Model model) {
        if (!isLoggedIn(session)) return "redirect:/";
        addStudentAttributes(session, model);
        return "learning-gap";
    }

    @GetMapping("/grade-calculator.html")
    public String gradeCalculator(HttpSession session, Model model) {
        if (!isLoggedIn(session)) return "redirect:/";
        addStudentAttributes(session, model);
        return "grade-calculator";
    }

    @GetMapping("/discussion.html")
    public String discussion(HttpSession session, Model model) {
        if (!isLoggedIn(session)) return "redirect:/";
        addStudentAttributes(session, model);
        String sid = (String) session.getAttribute("studentId");
        model.addAttribute("teachers", teacherRepository.findAll());
        model.addAttribute("discussions", discussionRepository.findByStudentIdOrderByAskedAtDesc(sid));
        return "discussion";
    }

    @GetMapping("/settings.html")
    public String settings(HttpSession session, Model model) {
        if (!isLoggedIn(session)) return "redirect:/";
        addStudentAttributes(session, model);
        String sid = (String) session.getAttribute("studentId");
        studentService.findByStudentId(sid).ifPresent(s -> {
            model.addAttribute("notifEmail", s.getNotifEmail() != null ? s.getNotifEmail() : "");
        });
        return "settings";
    }

    @PostMapping("/settings/save")
    public String saveSettings(@RequestParam(required = false) String notifEmail,
                               HttpSession session) {
        if (!isLoggedIn(session)) return "redirect:/";
        studentService.updateNotifEmail((String) session.getAttribute("studentId"), notifEmail);
        return "redirect:/settings.html?saved=true";
    }

    @GetMapping("/teacher-discussions.html")
    public String teacherDiscussions(HttpSession session, Model model) {
        if (!isTeacherLoggedIn(session)) return "redirect:/";
        String tid = (String) session.getAttribute("teacherId");
        model.addAttribute("teacherName", session.getAttribute("teacherName"));
        model.addAttribute("teacherId", tid);

        var all = discussionRepository.findByTeacherIdOrderByAskedAtDesc(tid);
        var unanswered = all.stream().filter(d -> d.getAnswer() == null).collect(Collectors.toList());
        var answered   = all.stream().filter(d -> d.getAnswer() != null).collect(Collectors.toList());

        model.addAttribute("unanswered", unanswered);
        model.addAttribute("answered",   answered);
        model.addAttribute("totalCount", all.size());
        return "teacher-discussions";
    }
}
