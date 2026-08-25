package com.example.eduadvisor2.service;

import com.example.eduadvisor2.model.Student;
import com.example.eduadvisor2.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;

    public Optional<Student> authenticate(String studentId, String password) {
        return studentRepository.findByStudentId(studentId)
                .filter(s -> s.getPassword().equals(password));
    }

    public Optional<Student> findByStudentId(String studentId) {
        return studentRepository.findByStudentId(studentId);
    }

    public List<Student> findAll() {
        return studentRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    public List<Student> findByBatch(String batchPrefix) {
        return studentRepository.findByStudentIdStartingWith(batchPrefix + "-");
    }

    public void updateNotifEmail(String studentId, String notifEmail) {
        studentRepository.findByStudentId(studentId).ifPresent(s -> {
            s.setNotifEmail(notifEmail != null && !notifEmail.isBlank() ? notifEmail.strip() : null);
            studentRepository.save(s);
        });
    }
}
