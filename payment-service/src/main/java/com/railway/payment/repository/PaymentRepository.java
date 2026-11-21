package com.railway.payment.repository;

import com.railway.payment.model.Payment;
import com.railway.payment.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTransactionId(String transactionId);

    Optional<Payment> findFirstByReservationIdAndStatus(Long reservationId, PaymentStatus status);

    Optional<Payment> findFirstByReservationIdAndStatusIn(Long reservationId, Collection<PaymentStatus> statuses);
}
