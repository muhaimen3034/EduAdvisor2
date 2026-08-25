package com.example.eduadvisor2.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "schedule_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String studentId;
    private String title;
    private String date;
    private String time;
    private String type;

    private LocalDateTime createdAt;

    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean notified = false;
}
