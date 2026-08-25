package com.example.eduadvisor2.repository;

import com.example.eduadvisor2.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByStudentIdOrderByBookedAtDesc(String studentId);
    List<Booking> findByTeacherNameOrderByBookedAtDesc(String teacherName);

    @Transactional
    void deleteByStudentId(String studentId);
}
