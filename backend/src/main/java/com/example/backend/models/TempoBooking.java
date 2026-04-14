package com.example.backend.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "tempo_bookings")
@Data
public class TempoBooking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    @JsonIgnore // Prevents infinite recursion
    @ToString.Exclude
    private Ticket ticket;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "description")
    private String description;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    public String getDuration() {
        if (startTime == null || endTime == null) {
            return "0m";
        }

        Duration duration = Duration.between(startTime, endTime);
        long totalMinutes = duration.toMinutes();

        if (totalMinutes <= 0) return "0m";

        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;

        if (hours > 0) {
            return hours + "h " + (minutes > 0 ? minutes + "m" : "");
        } else {
            return minutes + "m";
        }
    }
}