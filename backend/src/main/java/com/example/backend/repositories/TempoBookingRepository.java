package com.example.backend.repositories;

import com.example.backend.models.TempoBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TempoBookingRepository extends JpaRepository<TempoBooking, Long> {
    // Finds all bookings associated with a specific ticket ID
    List<TempoBooking> findByTicketId(Long ticketId);
}