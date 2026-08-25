package com.example.eduadvisor2.controller;

import com.example.eduadvisor2.model.Enrollment;
import com.example.eduadvisor2.repository.CourseRepository;
import com.example.eduadvisor2.repository.EnrollmentRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;

    @GetMapping
    public List<Enrollment> list(HttpSession session) {
        String studentId = (String) session.getAttribute("studentId");
        if (studentId == null) return List.of();
        return enrollmentRepository.findByStudentId(studentId);
    }

    @PostMapping
    public ResponseEntity<?> enroll(@RequestBody Map<String, String> body, HttpSession session) {
        String studentId = (String) session.getAttribute("studentId");
        if (studentId == null) return ResponseEntity.status(401).build();

        String courseCode = body.get("courseCode");

        if (enrollmentRepository.existsByStudentIdAndCourse_Code(studentId, courseCode))
            return ResponseEntity.badRequest().body(Map.of("error", "Already enrolled"));

        return courseRepository.findById(courseCode)
                .map(course -> {
                    Enrollment e = Enrollment.builder()
                            .studentId(studentId)
                            .course(course)
                            .enrolledAt(LocalDateTime.now())
                            .build();
                    enrollmentRepository.save(e);
                    return ResponseEntity.ok(Map.of("success", true, "id", e.getId()));
                })
                .orElse(ResponseEntity.badRequest().body(Map.of("error", "Course not found")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> drop(@PathVariable Long id, HttpSession session) {
        if (session.getAttribute("studentId") == null) return ResponseEntity.status(401).build();
        return enrollmentRepository.findById(id)
            .map(e -> {
                if ("CLOSED".equals(e.getCourse().getCreditType()))
                    return ResponseEntity.badRequest()
                        .body(Map.of("error", "Closed-credit courses cannot be dropped"));
                enrollmentRepository.deleteById(id);
                return ResponseEntity.ok(Map.of("success", true));
            })
            .orElseGet(() -> ResponseEntity.ok(Map.of("success", true)));
    }
}
