package com.example.eduadvisor2.controller;

import com.example.eduadvisor2.model.Course;
import com.example.eduadvisor2.repository.CourseRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseRepository courseRepository;

    @GetMapping
    public List<Course> list() {
        return courseRepository.findAll();
    }

    @GetMapping("/batch")
    public Map<String, Object> batchCourses(HttpSession session) {
        String batchGroup = (String) session.getAttribute("batchGroup");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("group", batchGroup != null ? batchGroup : "");
        if (batchGroup == null) {
            result.put("closed", List.of());
            result.put("extra",  List.of());
        } else {
            List<Course> all = courseRepository.findAll().stream()
                .filter(c -> batchGroup.equals(c.getBatchGroup()))
                .collect(Collectors.toList());
            result.put("closed", all.stream().filter(c -> "CLOSED".equals(c.getCreditType())).collect(Collectors.toList()));
            result.put("extra",  all.stream().filter(c -> "EXTRA".equals(c.getCreditType())).collect(Collectors.toList()));
        }
        return result;
    }
}
