package com.example.eduadvisor2.controller;

import com.example.eduadvisor2.model.Booking;
import com.example.eduadvisor2.repository.BookingRepository;
import com.example.eduadvisor2.service.NotificationService;
import com.example.eduadvisor2.service.StudentService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingRepository    bookingRepository;
    private final StudentService       studentService;
    private final NotificationService  notificationService;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> body, HttpSession session) {
        String studentId = (String) session.getAttribute("studentId");
        if (studentId == null) return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));

        Booking booking = Booking.builder()
                .studentId(studentId)
                .teacherName(body.get("teacherName"))
                .slot(body.get("slot"))
                .reason(body.get("reason"))
                .bookedAt(LocalDateTime.now())
                .build();
        bookingRepository.save(booking);

        // Send confirmation email
        studentService.findByStudentId(studentId).ifPresent(s -> {
            String email = (s.getNotifEmail() != null && !s.getNotifEmail().isBlank())
                    ? s.getNotifEmail() : s.getEmail();
            notificationService.notifyBookingConfirmed(
                    s.getName(), email, booking.getTeacherName(), booking.getSlot(), booking.getReason());
        });

        return ResponseEntity.ok(Map.of("success", true, "id", booking.getId()));
    }

    @GetMapping
    public List<Booking> list(HttpSession session) {
        String studentId = (String) session.getAttribute("studentId");
        if (studentId == null) return List.of();
        return bookingRepository.findByStudentIdOrderByBookedAtDesc(studentId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpSession session) {
        if (session.getAttribute("studentId") == null) return ResponseEntity.status(401).build();
        bookingRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/all")
    public ResponseEntity<?> deleteAll(HttpSession session) {
        String studentId = (String) session.getAttribute("studentId");
        if (studentId == null) return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        bookingRepository.deleteByStudentId(studentId);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
