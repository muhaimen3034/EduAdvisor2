package com.example.eduadvisor2.controller;

import com.example.eduadvisor2.model.FeeStatus;
import com.example.eduadvisor2.repository.FeeStatusRepository;
import com.example.eduadvisor2.repository.TeacherRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/fee")
@RequiredArgsConstructor
public class FeeController {

    private final FeeStatusRepository feeRepository;
    private final TeacherRepository   teacherRepository;

    // ── Student: get own fee status ────────────────────────────────────────
    @GetMapping("/status")
    public Map<String, Object> status(HttpSession session) {
        String studentId = (String) session.getAttribute("studentId");
        if (studentId == null) return Map.of("status", "NOT_LOGGED_IN");

        return feeRepository.findByStudentId(studentId)
                .map(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("status",          f.getStatus());
                    m.put("submittedAt",     f.getSubmittedAt() != null ? f.getSubmittedAt().toString() : null);
                    m.put("approvedAt",      f.getApprovedAt()  != null ? f.getApprovedAt().toString()  : null);
                    m.put("coordinatorName", f.getCoordinatorName());
                    return m;
                })
                .orElse(Map.of("status", "NOT_SUBMITTED"));
    }

    // ── Student: submit fee confirmation ───────────────────────────────────
    @PostMapping("/submit")
    public ResponseEntity<?> submit(HttpSession session) {
        String studentId   = (String) session.getAttribute("studentId");
        String studentName = (String) session.getAttribute("studentName");
        if (studentId == null) return ResponseEntity.status(401).build();

        if (feeRepository.findByStudentId(studentId).isPresent()) {
            return ResponseEntity.ok(Map.of("message", "Already submitted"));
        }

        FeeStatus fee = FeeStatus.builder()
                .studentId(studentId)
                .studentName(studentName != null ? studentName : studentId)
                .status("SUBMITTED")
                .submittedAt(LocalDateTime.now())
                .build();
        feeRepository.save(fee);
        return ResponseEntity.ok(Map.of("success", true, "status", "SUBMITTED"));
    }

    // ── Coordinator: list all submissions ──────────────────────────────────
    @GetMapping("/all")
    public List<FeeStatus> all(HttpSession session) {
        if (session.getAttribute("teacherId") == null) return List.of();
        return feeRepository.findAllByOrderBySubmittedAtDesc();
    }

    // ── Coordinator: approve a student's fee ───────────────────────────────
    @PostMapping("/approve/{studentId}")
    public ResponseEntity<?> approve(@PathVariable String studentId, HttpSession session) {
        String teacherId   = (String) session.getAttribute("teacherId");
        String teacherName = (String) session.getAttribute("teacherName");
        if (teacherId == null) return ResponseEntity.status(401).build();

        feeRepository.findByStudentId(studentId).ifPresent(fee -> {
            fee.setStatus("APPROVED");
            fee.setApprovedAt(LocalDateTime.now());
            fee.setCoordinatorId(teacherId);
            fee.setCoordinatorName(teacherName != null ? teacherName : teacherId);
            feeRepository.save(fee);
        });
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── Coordinator: reject (revert to NOT_SUBMITTED) ──────────────────────
    @DeleteMapping("/approve/{studentId}")
    public ResponseEntity<?> reject(@PathVariable String studentId, HttpSession session) {
        if (session.getAttribute("teacherId") == null) return ResponseEntity.status(401).build();
        feeRepository.findByStudentId(studentId).ifPresent(feeRepository::delete);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
