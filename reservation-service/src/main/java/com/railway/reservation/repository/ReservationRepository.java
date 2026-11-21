package com.railway.reservation.repository;

import com.railway.reservation.model.Reservation;
import com.railway.reservation.model.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByPnr(String pnr);

    boolean existsByPnr(String pnr);

    /** Locks the reservation row until the transaction ends (used by cancellation, so two cancels of one ticket queue up). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.pnr = :pnr")
    Optional<Reservation> findByPnrForUpdate(@Param("pnr") String pnr);

    List<Reservation> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Reservation> findAllByOrderByCreatedAtDesc();

    List<Reservation> findByStatusOrderByCreatedAtDesc(ReservationStatus status);

    List<Reservation> findByStatusAndCreatedAtBefore(ReservationStatus status, LocalDateTime cutoff);
}
