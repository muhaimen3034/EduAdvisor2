package com.example.eduadvisor2.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "fee_statuses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String studentId;

    private String studentName;

    private String status; // "SUBMITTED" | "APPROVED"

    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private String coordinatorId;
    private String coordinatorName;
}
