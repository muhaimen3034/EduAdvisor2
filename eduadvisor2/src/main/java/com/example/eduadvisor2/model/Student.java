package com.example.eduadvisor2.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "students")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String studentId;

    private String name;
    private String password;
    private String program;
    private Double gpa;
    private Integer creditsCompleted;
    private String academicYear;
    private String email;

    private String notifEmail;  // personal email for notifications
}
