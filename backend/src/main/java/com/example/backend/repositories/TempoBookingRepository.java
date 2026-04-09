package com.example.backend.repositories;

import com.example.backend.models.TempoBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TempoBookingRepository extends JpaRepository<TempoBooking, Long> {
}