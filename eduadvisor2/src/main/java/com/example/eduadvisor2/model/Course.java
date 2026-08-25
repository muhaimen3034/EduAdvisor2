package com.example.eduadvisor2.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "courses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Course {

    @Id
    private String code;

    private String name;
    private String instructor;
    private String room;
    private int credits;
    private String category;
    private String color;
    private String days;       // comma-separated day numbers, e.g. "1,3"
    private String startTime;  // e.g. "09:00"
    private String endTime;    // e.g. "10:30"
    private String teacherId;   // references Teacher.teacherId, e.g. "TCH-001"
    private String creditType;  // "CLOSED" = mandatory batch credit, "EXTRA" = optional, null = general
    private String batchGroup;  // "251", "241", or null (all students)
}
