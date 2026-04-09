package com.example.backend.models;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "tickets")
@Data
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_key", nullable = false)
    private String projectKey;

    @Column(name = "ticket_number", nullable = false)
    private Long ticketNumber;

    @Column(name = "issue_type")
    private String issueType;

    private String summary;
    private String description;
    private String assignee;
    private String reporter;
    private String priority;
    private String status;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    // Fetch all comments and bookings automatically when loading a ticket
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<Comment> comments;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JsonManagedReference
    @ToString.Exclude
    private List<TempoBooking> tempoBookings;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}