package com.example.eduadvisor2.config;

import com.example.eduadvisor2.model.*;
import com.example.eduadvisor2.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final StudentRepository         studentRepository;
    private final TeacherRepository         teacherRepository;
    private final CourseRepository          courseRepository;
    private final EnrollmentRepository      enrollmentRepository;
    private final StudentActivityRepository activityRepository;

    @Override
    public void run(String... args) {
        seedStudent();
        seedTeacher();
        seedCourses();
        seedBatchCourses();
        migrateBatchEnrollments();
        patchAdvisor();
        seedStudentActivities();
    }

    private void seedTeacher() {
        if (teacherRepository.count() > 0) return;
        List.of(
            t("TCH-001","Mahbub Hossain",    "Computer Science","Associate Professor","mahbub.hossain@diu.edu.bd",  false),
            t("TCH-002","Mehedi Hasan",       "Computer Science","Assistant Professor","mehedi.hasan@diu.edu.bd",   false),
            t("TCH-003","Nasimul K. Sohel",   "Computer Science","Senior Lecturer",    "nasimul.sohel@diu.edu.bd",  false),
            t("TCH-004","Dr. Ahmed",           "Mathematics",     "Professor",          "dr.ahmed@diu.edu.bd",       false),
            t("TCH-005","Dr. Fatema Khanam",   "Computer Science","Associate Professor","fatema.khanam@diu.edu.bd",  false),
            t("TCH-006","Dr. Rahman",          "Computer Science","Professor",          "dr.rahman@diu.edu.bd",      false),
            t("TCH-007","Sarwar Hossain",      "Computer Science","Lecturer",           "sarwar.hossain@diu.edu.bd", false),
            t("TCH-008","Md. Azam Khan",       "Computer Science","Lecturer",           "md.azam.khan@diu.edu.bd",   false),
            t("TCH-009","Md. Israfil",         "Computer Science","Associate Professor","israfil@diu.edu.bd",        true)
        ).forEach(teacherRepository::save);
    }

    private void patchAdvisor() {
        // Add Israfil if not already present (for existing databases)
        if (teacherRepository.findByTeacherId("TCH-009").isEmpty()) {
            teacherRepository.save(
                t("TCH-009","Md. Israfil","Computer Science","Associate Professor","israfil@diu.edu.bd",true)
            );
        }
        // Transfer coordinator flag: Dr. Rahman → Israfil
        teacherRepository.findByTeacherId("TCH-006").ifPresent(rahman -> {
            if (rahman.isCoordinator()) { rahman.setCoordinator(false); teacherRepository.save(rahman); }
        });
        teacherRepository.findByTeacherId("TCH-009").ifPresent(israfil -> {
            if (!israfil.isCoordinator()) { israfil.setCoordinator(true); teacherRepository.save(israfil); }
        });
    }

    private Teacher t(String id, String name, String dept, String designation, String email, boolean coordinator) {
        return Teacher.builder()
                .teacherId(id).name(name).password("teach123")
                .department(dept).designation(designation).email(email)
                .coordinator(coordinator)
                .build();
    }

    // The exact 20 students that must always be present
    private static final List<Object[]> BATCH_STUDENTS = List.of(
        new Object[]{"251-16-056","Muhaimen Islam Nishargo","password123","3rd",72,3.72},
        new Object[]{"251-16-057","Rafid Ahmed",            "pass1234",   "3rd",66,3.45},
        new Object[]{"251-16-058","Tasnim Hossain",         "pass1234",   "2nd",45,3.68},
        new Object[]{"251-16-059","Iffat Jahan",            "pass1234",   "2nd",42,3.20},
        new Object[]{"251-16-060","Rayan Khan",             "pass1234",   "1st",18,2.85},
        new Object[]{"251-16-061","Nafisa Islam",           "pass1234",   "3rd",75,3.90},
        new Object[]{"251-16-062","Sazzad Hossain",         "pass1234",   "2nd",48,2.40},
        new Object[]{"251-16-063","Maliha Akter",           "pass1234",   "3rd",72,3.55},
        new Object[]{"251-16-064","Touhid Rahman",          "pass1234",   "1st",21,2.10},
        new Object[]{"251-16-065","Sumaya Noor",            "pass1234",   "2nd",45,3.75},
        new Object[]{"241-16-101","Nabila Akter",           "pass1234",   "4th",96,3.81},
        new Object[]{"241-16-102","Farhan Rahman",          "pass1234",   "2nd",48,3.30},
        new Object[]{"241-16-103","Sumaiya Begum",          "pass1234",   "3rd",69,3.55},
        new Object[]{"241-16-104","Ariful Hasan",           "pass1234",   "3rd",66,2.90},
        new Object[]{"241-16-105","Samiha Akter",           "pass1234",   "2nd",39,3.15},
        new Object[]{"241-16-106","Imran Hossain",          "pass1234",   "4th",99,2.60},
        new Object[]{"241-16-107","Tashfia Nusrat",         "pass1234",   "1st",18,3.50},
        new Object[]{"241-16-108","Jubayer Ahmed",          "pass1234",   "3rd",75,1.95},
        new Object[]{"241-16-109","Faria Islam",            "pass1234",   "2nd",45,3.80},
        new Object[]{"241-16-110","Sabbir Hossain",         "pass1234",   "1st",15,2.20}
    );

    private void seedStudent() {
        Set<String> targetIds = Set.of(
            "251-16-056","251-16-057","251-16-058","251-16-059","251-16-060",
            "251-16-061","251-16-062","251-16-063","251-16-064","251-16-065",
            "241-16-101","241-16-102","241-16-103","241-16-104","241-16-105",
            "241-16-106","241-16-107","241-16-108","241-16-109","241-16-110"
        );
        // Remove students whose IDs are not in the target set (old/stale records)
        studentRepository.findAll().stream()
            .filter(st -> !targetIds.contains(st.getStudentId()))
            .forEach(st -> {
                enrollmentRepository.findByStudentId(st.getStudentId())
                    .forEach(enrollmentRepository::delete);
                studentRepository.delete(st);
            });
        // Upsert: if student already exists reuse its auto-generated id so JPA does UPDATE not INSERT
        BATCH_STUDENTS.forEach(row -> {
            String sid = (String) row[0];
            Student newData = s(sid, (String)row[1], (String)row[2],
                                (String)row[3], (int)row[4], (double)row[5]);
            studentRepository.findByStudentId(sid)
                .ifPresent(existing -> newData.setId(existing.getId()));
            studentRepository.save(newData);
        });
    }

    private Student s(String id, String name, String pass, String year, int credits, double gpa) {
        return Student.builder()
                .studentId(id).name(name).password(pass)
                .program("BSc Computer Information & Systems")
                .academicYear(year).creditsCompleted(credits).gpa(gpa)
                .email(id + "@diu.edu.bd")
                .build();
    }

    private void seedBatchCourses() {
        // Upsert: Course @Id is the code string, so save() merges if exists
        List.of(
            // ── Batch 251 — Closed Credit (mandatory) ──────────────────────────
            bc("B251DB",  "Database Management",            3, "Md. Azam Khan",    "Block C - 301", "c-blue",   "1,3", "08:30","10:00", "Computer Science", "TCH-008", "CLOSED", "251"),
            bc("B251OOP", "Object Oriented Programming",    3, "Mahbub Hossain",   "Block C - 302", "c-purple", "2,4", "09:00","10:30", "Computer Science", "TCH-001", "CLOSED", "251"),
            bc("B251ISE", "Information Systems Engineering",3, "Mehedi Hasan",     "Block C - 303", "c-cyan",   "1,3", "11:00","12:30", "Computer Science", "TCH-002", "CLOSED", "251"),
            // ── Batch 251 — Extra (optional) ───────────────────────────────────
            bc("B251ISA", "Information Systems Architecture",2,"Nasimul K. Sohel", "Block C - 304", "c-green",  "2,4", "13:00","14:30", "Computer Science", "TCH-003", "EXTRA",  "251"),
            bc("B251IOT", "Basic of IoT",                   2, "Sarwar Hossain",   "Block C - 305", "c-amber",  "3,5", "14:30","16:00", "Computer Science", "TCH-007", "EXTRA",  "251"),
            bc("B251RES", "Basic of Research",              2, "Dr. Fatema Khanam","Block C - 306", "c-purple", "1",   "15:00","17:00", "Computer Science", "TCH-005", "EXTRA",  "251"),
            // ── Batch 241 — Closed Credit (mandatory) ──────────────────────────
            bc("B241DSA", "Data Structures & Algorithms",   3, "Mehedi Hasan",     "Block C - 401", "c-blue",   "2,4", "08:00","09:30", "Computer Science", "TCH-002", "CLOSED", "241"),
            bc("B241ISA", "Information Systems Architecture",3,"Nasimul K. Sohel", "Block C - 402", "c-green",  "1,3", "09:00","10:30", "Computer Science", "TCH-003", "CLOSED", "241"),
            bc("B241DA",  "Data Analysis",                  3, "Dr. Fatema Khanam","Block C - 403", "c-cyan",   "2,4", "11:00","12:30", "Computer Science", "TCH-005", "CLOSED", "241"),
            // ── Batch 241 — Extra (optional) ───────────────────────────────────
            bc("B241STAT","Statistics",                     2, "Dr. Ahmed",        "Block A - 105", "c-amber",  "1,3", "13:00","14:30", "Mathematics",      "TCH-004", "EXTRA",  "241"),
            bc("B241ECON","Economics",                      2, "Dr. Rahman",       "Block AB4-103", "c-purple", "2,4", "14:30","16:00", "General",          "TCH-006", "EXTRA",  "241")
        ).forEach(courseRepository::save);
    }

    private void seedCourses() {
        if (courseRepository.count() > 0) return;
        List.of(
            c("CS101",   "Introduction to Programming",  3, "Dr. Rahman",       "Block C - 101",   "c-blue",   "1,3", "08:00","09:30","Computer Science","TCH-006"),
            c("CS201",   "Object-Oriented Programming",  3, "Mahbub Hossain",    "Block C - 102",   "c-purple", "2,4", "09:00","10:30","Computer Science","TCH-001"),
            c("CS301",   "Data Structures & Algorithms", 3, "Mehedi Hasan",      "Block C - 201",   "c-blue",   "1,3", "09:00","10:30","Computer Science","TCH-002"),
            c("CS302",   "Software Engineering",         3, "Nasimul K. Sohel",  "Block C - 203",   "c-purple", "2,4", "11:00","12:30","Computer Science","TCH-003"),
            c("CS303",   "Database Systems",             3, "Nasimul K. Sohel",  "Block C - 203",   "c-green",  "1,3", "14:00","15:30","Computer Science","TCH-003"),
            c("CS304",   "Artificial Intelligence",      3, "Mehedi Hasan",      "Block C - 205",   "c-cyan",   "3,5", "13:00","14:30","Computer Science","TCH-002"),
            c("CS305",   "Computer Networks",            3, "Sarwar Hossain",    "Block C - 204",   "c-green",  "2,4", "08:00","09:30","Computer Science","TCH-007"),
            c("CS306",   "Operating Systems",            3, "Md. Azam Khan",     "Block C - 206",   "c-amber",  "1,3", "11:00","12:30","Computer Science","TCH-008"),
            c("CS307",   "Web Development",              3, "Mahbub Hossain",    "Block C - 201",   "c-purple", "2,4", "15:30","17:00","Computer Science","TCH-001"),
            c("CS401",   "Machine Learning",             3, "Dr. Fatema Khanam", "Block C - 207",   "c-blue",   "1,3", "16:00","17:30","Computer Science","TCH-005"),
            c("CS402",   "Cloud Computing",              3, "Dr. Rahman",        "Block C - 208",   "c-cyan",   "2,4", "16:00","17:30","Computer Science","TCH-006"),
            c("MATH101", "Calculus I",                   3, "Dr. Ahmed",         "Block A - 101",   "c-amber",  "2,4", "08:00","09:30","Mathematics",     "TCH-004"),
            c("MATH102", "Calculus II",                  3, "Dr. Ahmed",         "Block A - 102",   "c-amber",  "1,3", "08:00","09:30","Mathematics",     "TCH-004"),
            c("MATH201", "Statistics & Probability",     3, "Dr. Ahmed",         "Block A - 106",   "c-amber",  "2,4", "14:00","16:00","Mathematics",     "TCH-004"),
            c("MATH202", "Discrete Mathematics",         3, "Dr. Ahmed",         "Block A - 103",   "c-green",  "1,3", "11:00","12:30","Mathematics",     "TCH-004"),
            c("MATH301", "Linear Algebra",               3, "Dr. Ahmed",         "Block A - 104",   "c-blue",   "2,4", "13:00","14:30","Mathematics",     "TCH-004"),
            c("ENG101",  "English Communication",        3, "Sarwar Hossain",    "Block AB4 - 301", "c-purple", "1,3", "12:00","13:30","General",         "TCH-007"),
            c("HUM201",  "Professional Ethics",          2, "Md. Azam Khan",     "Block AB4 - 202", "c-green",  "4",   "13:00","15:00","General",         "TCH-008"),
            c("PHY101",  "Physics I",                    3, "Dr. Ahmed",         "Block A - 201",   "c-cyan",   "2,4", "10:00","11:30","General",         "TCH-004"),
            c("BUS101",  "Business Communication",       3, "Dr. Rahman",        "Block AB4 - 101", "c-amber",  "1,5", "15:00","16:30","General",         "TCH-006")
        ).forEach(courseRepository::save);
    }

    private static final String[] BATCH_251_IDS = {
        "251-16-056","251-16-057","251-16-058","251-16-059","251-16-060",
        "251-16-061","251-16-062","251-16-063","251-16-064","251-16-065"
    };
    private static final String[] BATCH_241_IDS = {
        "241-16-101","241-16-102","241-16-103","241-16-104","241-16-105",
        "241-16-106","241-16-107","241-16-108","241-16-109","241-16-110"
    };

    private void migrateBatchEnrollments() {
        // Remove any general (non-batch) course enrollments from batch students
        for (String sid : BATCH_251_IDS) removeGeneralEnrollments(sid);
        for (String sid : BATCH_241_IDS) removeGeneralEnrollments(sid);

        // Ensure every batch 251 student is enrolled in their 3 closed-credit courses
        for (String sid : BATCH_251_IDS) ensureEnrolled(sid, "B251DB", "B251OOP", "B251ISE");
        // Ensure every batch 241 student is enrolled in their 3 closed-credit courses
        for (String sid : BATCH_241_IDS) ensureEnrolled(sid, "B241DSA", "B241ISA", "B241DA");
    }

    private void removeGeneralEnrollments(String studentId) {
        enrollmentRepository.findByStudentId(studentId).stream()
            .filter(e -> e.getCourse().getBatchGroup() == null)
            .forEach(enrollmentRepository::delete);
    }

    private void ensureEnrolled(String studentId, String... codes) {
        for (String code : codes) {
            if (!enrollmentRepository.existsByStudentIdAndCourse_Code(studentId, code)) {
                courseRepository.findById(code).ifPresent(course ->
                    enrollmentRepository.save(Enrollment.builder()
                        .studentId(studentId)
                        .course(course)
                        .enrolledAt(LocalDateTime.now())
                        .build())
                );
            }
        }
    }

    private Course c(String code, String name, int credits, String instructor,
                     String room, String color, String days, String start, String end,
                     String cat, String teacherId) {
        return Course.builder()
                .code(code).name(name).credits(credits).instructor(instructor)
                .room(room).color(color).days(days).startTime(start).endTime(end)
                .category(cat).teacherId(teacherId)
                .build();
    }

    private void seedStudentActivities() {
        // Seed demo activity for Muhaimen Islam Nishargo (251-16-056)
        if (activityRepository.findById("251-16-056").isEmpty()) {
            activityRepository.save(StudentActivity.builder()
                .studentId("251-16-056")
                .linkedinUrl("https://www.linkedin.com/public-profile/settings/?trk=d_flagship3_profile_self_view_public_profile")
                .linkedinScore(72)
                .clubScore(65)
                .clubDescription("DIU CS Club, Programming Contest Team (ICPC aspirant)")
                .volunteeringScore(48)
                .volunteeringDescription("Campus tech workshop facilitator, free coding tutorials for juniors")
                .skillsScore(80)
                .skillsDescription("Java, Spring Boot, Python, MySQL, Web Development, Git")
                .researchScore(55)
                .researchDescription("Building EduAdvisor — AI-integrated academic management system")
                .leadershipScore(58)
                .leadershipDescription("Project team lead, senior member of DIU CS Club")
                .updatedAt(LocalDateTime.now())
                .build());
        }
        // Seed minimal demo for a few batch-241 students
        if (activityRepository.findById("241-16-101").isEmpty()) {
            activityRepository.save(StudentActivity.builder()
                .studentId("241-16-101")
                .linkedinScore(60).clubScore(70).volunteeringScore(55)
                .skillsScore(65).researchScore(40).leadershipScore(75)
                .updatedAt(LocalDateTime.now()).build());
        }
    }

    private Course bc(String code, String name, int credits, String instructor,
                      String room, String color, String days, String start, String end,
                      String cat, String teacherId, String creditType, String batchGroup) {
        return Course.builder()
                .code(code).name(name).credits(credits).instructor(instructor)
                .room(room).color(color).days(days).startTime(start).endTime(end)
                .category(cat).teacherId(teacherId)
                .creditType(creditType).batchGroup(batchGroup)
                .build();
    }
}
