package com.example.eduadvisor2.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "teachers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String teacherId;
    private String name;
    private String password;
    private String department;
    private String designation;
    private String email;
    private boolean coordinator;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] photo;
    private String photoType;
}
