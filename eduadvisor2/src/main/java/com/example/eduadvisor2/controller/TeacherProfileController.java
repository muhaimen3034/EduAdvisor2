package com.example.eduadvisor2.controller;

import com.example.eduadvisor2.model.Teacher;
import com.example.eduadvisor2.repository.TeacherRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/teachers")
@RequiredArgsConstructor
public class TeacherProfileController {

    private final TeacherRepository teacherRepository;

    @GetMapping
    public List<Map<String, Object>> list() {
        return teacherRepository.findAll().stream()
                .map(this::toProfile)
                .toList();
    }

    @GetMapping("/{teacherId}")
    public ResponseEntity<?> get(@PathVariable String teacherId) {
        return teacherRepository.findByTeacherId(teacherId)
                .map(t -> ResponseEntity.ok((Object) toProfile(t)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{teacherId}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable String teacherId) {
        Optional<Teacher> opt = teacherRepository.findByTeacherId(teacherId);
        if (opt.isEmpty() || opt.get().getPhoto() == null) {
            return ResponseEntity.notFound().build();
        }
        Teacher t = opt.get();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        t.getPhotoType() != null ? t.getPhotoType() : "image/jpeg"))
                .body(t.getPhoto());
    }

    @PostMapping("/{teacherId}/photo")
    public ResponseEntity<?> uploadPhoto(@PathVariable String teacherId,
                                         @RequestParam("file") MultipartFile file,
                                         HttpSession session) {
        String sessionTeacherId = (String) session.getAttribute("teacherId");
        if (sessionTeacherId == null) return ResponseEntity.status(401).build();
        if (!sessionTeacherId.equals(teacherId)) return ResponseEntity.status(403).build();

        Optional<Teacher> opt = teacherRepository.findByTeacherId(teacherId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Teacher t = opt.get();
        try {
            t.setPhoto(file.getBytes());
            t.setPhotoType(file.getContentType());
            teacherRepository.save(t);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> toProfile(Teacher t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("teacherId",   t.getTeacherId());
        m.put("name",        t.getName());
        m.put("department",  t.getDepartment());
        m.put("designation", t.getDesignation());
        m.put("email",       t.getEmail());
        m.put("coordinator", t.isCoordinator());
        m.put("hasPhoto",    t.getPhoto() != null);
        return m;
    }
}
