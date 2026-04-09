package com.example.backend.repositories;

import com.example.backend.models.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    // Spring magically turns this method name into a SQL query searching both columns!
    Optional<Ticket> findByProjectKeyAndTicketNumber(String projectKey, Long ticketNumber);
}