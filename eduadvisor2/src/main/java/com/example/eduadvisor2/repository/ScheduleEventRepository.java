package com.example.eduadvisor2.repository;

import com.example.eduadvisor2.model.ScheduleEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduleEventRepository extends JpaRepository<ScheduleEvent, Long> {
    List<ScheduleEvent> findByStudentIdOrderByDateAsc(String studentId);
    List<ScheduleEvent> findByDateAndNotifiedFalse(String date);
}
