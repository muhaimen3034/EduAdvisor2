package com.example.eduadvisor2.controller;

import com.example.eduadvisor2.model.ScheduleEvent;
import com.example.eduadvisor2.repository.ScheduleEventRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class ScheduleEventController {

    private final ScheduleEventRepository eventRepository;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> body, HttpSession session) {
        String studentId = (String) session.getAttribute("studentId");
        if (studentId == null) return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));

        ScheduleEvent event = ScheduleEvent.builder()
                .studentId(studentId)
                .title(body.get("title"))
                .date(body.get("date"))
                .time(body.get("time"))
                .type(body.get("type"))
                .createdAt(LocalDateTime.now())
                .build();
        eventRepository.save(event);
        return ResponseEntity.ok(Map.of("success", true, "id", event.getId()));
    }

    @GetMapping
    public List<ScheduleEvent> list(HttpSession session) {
        String studentId = (String) session.getAttribute("studentId");
        if (studentId == null) return List.of();
        return eventRepository.findByStudentIdOrderByDateAsc(studentId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpSession session) {
        if (session.getAttribute("studentId") == null) return ResponseEntity.status(401).build();
        eventRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
