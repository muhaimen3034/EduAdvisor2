package com.example.eduadvisor2.controller;

import com.example.eduadvisor2.repository.TeacherRepository;
import com.example.eduadvisor2.service.StudentService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final StudentService    studentService;
    private final TeacherRepository teacherRepository;

    @GetMapping("/")
    public String loginPage() {
        return "index";
    }

    @PostMapping("/login")
    public String login(@RequestParam String studentId,
                        @RequestParam String password,
                        HttpSession session,
                        Model model) {
        return studentService.authenticate(studentId, password)
                .map(student -> {
                    String sid = student.getStudentId();
                    session.setAttribute("studentId", sid);
                    session.setAttribute("studentName", student.getName());
                    session.setAttribute("role", "student");
                    String bg = sid.startsWith("251-") ? "251" : sid.startsWith("241-") ? "241" : null;
                    session.setAttribute("batchGroup", bg);
                    return "redirect:/dashboard.html";
                })
                .orElseGet(() -> {
                    model.addAttribute("error", "Invalid Student ID or password.");
                    model.addAttribute("activeRole", "student");
                    return "index";
                });
    }

    @PostMapping("/teacher-login")
    public String teacherLogin(@RequestParam String teacherId,
                               @RequestParam String password,
                               HttpSession session,
                               Model model) {
        return teacherRepository.findByTeacherId(teacherId)
                .filter(t -> t.getPassword().equals(password))
                .map(teacher -> {
                    session.setAttribute("teacherId", teacher.getTeacherId());
                    session.setAttribute("teacherName", teacher.getName());
                    session.setAttribute("role", "teacher");
                    String tname = teacher.getName() != null ? teacher.getName().toLowerCase() : "";
                    String advisorBatch = tname.contains("azam") ? "251"
                                       : tname.contains("nasimul") ? "241" : null;
                    session.setAttribute("advisorBatch", advisorBatch);
                    return "redirect:/teacher-dashboard.html";
                })
                .orElseGet(() -> {
                    model.addAttribute("error", "Invalid Teacher ID or password.");
                    model.addAttribute("activeRole", "teacher");
                    return "index";
                });
    }

    @PostMapping("/coord-login")
    public String coordLogin(@RequestParam String username,
                             @RequestParam String password,
                             HttpSession session,
                             Model model) {
        if ("coordinator".equals(username) && "coord123".equals(password)) {
            session.setAttribute("coordinatorId",   "COORD-001");
            session.setAttribute("coordinatorName", "Campus Coordinator");
            session.setAttribute("role", "coordinator");
            return "redirect:/coordinator-dashboard.html";
        }
        model.addAttribute("error", "Invalid coordinator credentials.");
        model.addAttribute("activeRole", "coordinator");
        return "index";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }
}
