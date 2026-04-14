package com.example.backend.services;

import com.example.backend.models.TempoBooking;
import com.example.backend.repositories.TempoBookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TempoBookingService {

    private final TempoBookingRepository tempoBookingRepository;

    public TempoBookingService(TempoBookingRepository tempoBookingRepository) {
        this.tempoBookingRepository = tempoBookingRepository;
    }

    public List<TempoBooking> getBookingsByTicketId(Long ticketId) {
        return tempoBookingRepository.findByTicketId(ticketId);
    }

    @Transactional
    public Optional<TempoBooking> updateBooking(Long id, TempoBooking updatedDetails) {
        return tempoBookingRepository.findById(id).map(existingBooking -> {
            // Update fields
            existingBooking.setUserName(updatedDetails.getUserName());
            existingBooking.setDescription(updatedDetails.getDescription());
            existingBooking.setStartTime(updatedDetails.getStartTime());
            existingBooking.setEndTime(updatedDetails.getEndTime());

            return tempoBookingRepository.save(existingBooking);
        });
    }
}