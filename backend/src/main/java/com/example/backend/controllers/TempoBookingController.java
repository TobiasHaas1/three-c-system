package com.example.backend.controllers;

import com.example.backend.models.TempoBooking;
import com.example.backend.services.TempoBookingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tempo-bookings")
@CrossOrigin(origins = "http://localhost:4200")
public class TempoBookingController {

    private final TempoBookingService tempoBookingService;

    public TempoBookingController(TempoBookingService tempoBookingService) {
        this.tempoBookingService = tempoBookingService;
    }

    // 1. GET all bookings for a specific ticket
    // URL: GET /api/tickets/{ticketId}/tempo-bookings
    @GetMapping("/ticket/{ticketId}")
    public ResponseEntity<List<TempoBooking>> getByTicket(@PathVariable Long ticketId) {
        return ResponseEntity.ok(tempoBookingService.getBookingsByTicketId(ticketId));
    }

    // 2. UPDATE a booking (text, start time, end time)
    // URL: PUT /api/tempo-bookings/{id}
    @PutMapping("/{id}")
    public ResponseEntity<TempoBooking> updateBooking(
            @PathVariable Long id,
            @RequestBody TempoBooking bookingDetails) {
        return tempoBookingService.updateBooking(id, bookingDetails)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}