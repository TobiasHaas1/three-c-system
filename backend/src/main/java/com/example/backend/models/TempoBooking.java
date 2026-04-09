package com.example.backend.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString; // Add this import
import java.time.LocalDate;

@Entity
@Table(name = "tempo_bookings")
@Data
public class TempoBooking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    @JsonIgnore
    @ToString.Exclude
    private Ticket ticket;

    @Column(name = "user_name")
    private String userName;

    private String duration;
    private String description;

    @Column(name = "booking_date")
    private LocalDate bookingDate;
}