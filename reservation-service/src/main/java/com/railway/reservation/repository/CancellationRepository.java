package com.railway.reservation.repository;

import com.railway.reservation.model.Cancellation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CancellationRepository extends JpaRepository<Cancellation, Long> {

    List<Cancellation> findByReservationId(Long reservationId);
}
