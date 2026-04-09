package com.example.backend.controllers;

import com.example.backend.enums.TicketStatus;
import com.example.backend.models.TempoBooking;
import com.example.backend.models.Ticket;
import com.example.backend.services.TicketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
@CrossOrigin(origins = "http://localhost:4200")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    // --- 1. GET ALL TICKETS ---
    @GetMapping
    public ResponseEntity<List<Ticket>> getAllTickets() {
        return ResponseEntity.ok(ticketService.getAllTickets());
    }

    // --- 2. GET SINGLE TICKET BY KEY ---
    @GetMapping("/{fullKey}")
    public ResponseEntity<Ticket> getTicketByKey(@PathVariable String fullKey) {
        return ticketService.getTicketByKey(fullKey)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // --- 3. CREATE NEW TICKET ---
    @PostMapping
    public ResponseEntity<Ticket> createTicket(@RequestBody Ticket ticket) {
        return ResponseEntity.ok(ticketService.createTicket(ticket));
    }

    // --- 4. ADD TEMPO BOOKING ---
    @PostMapping("/{id}/tempo")
    public ResponseEntity<TempoBooking> addTempoBooking(@PathVariable Long id, @RequestBody TempoBooking booking) {
        return ticketService.addTempoBooking(id, booking)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // --- 5. UPDATE STATUS & TRIGGER BILLING ---
    @PatchMapping("/{id}/status")
    public ResponseEntity<Ticket> updateTicketStatus(@PathVariable Long id, @RequestBody Map<String, TicketStatus> requestBody) {
        TicketStatus newStatus = requestBody.get("status");

        if (newStatus == null) {
            return ResponseEntity.badRequest().build();
        }

        return ticketService.updateTicketStatus(id, newStatus)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}