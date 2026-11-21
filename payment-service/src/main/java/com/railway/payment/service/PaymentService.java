package com.railway.payment.service;

import com.railway.payment.dto.PaymentResultRequest;
import com.railway.payment.dto.RefundRequest;
import com.railway.payment.dto.ReservationSummary;
import com.railway.payment.error.ApiException;
import com.railway.payment.util.RailwayTime;
import com.railway.payment.client.ReservationClient;
import com.railway.payment.dto.CallbackRequest;
import com.railway.payment.dto.PaymentRequest;
import com.railway.payment.dto.PaymentResponse;
import com.railway.payment.model.Payment;
import com.railway.payment.model.PaymentStatus;
import com.railway.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final ReservationClient reservationClient;

    public PaymentService(PaymentRepository paymentRepository, ReservationClient reservationClient) {
        this.paymentRepository = paymentRepository;
        this.reservationClient = reservationClient;
    }

    public PaymentResponse initiate(Long userId, PaymentRequest request) {
        // The amount always comes from the reservation - never from the client.
        ReservationSummary reservation = reservationClient.getSummary(request.reservationId());
        if (!reservation.userId().equals(userId)) {
            throw ApiException.forbidden("This reservation belongs to another user");
        }
        if (!"PENDING_PAYMENT".equals(reservation.status())) {
            throw ApiException.conflict("Reservation is " + reservation.status()
                    + "; payment is only possible while it is PENDING_PAYMENT");
        }

        Optional<Payment> existing = paymentRepository
                .findFirstByReservationIdAndStatus(request.reservationId(), PaymentStatus.INITIATED);
        if (existing.isPresent()) {
            return toResponse(existing.get(), "Payment already initiated - complete it on the gateway");
        }

        Payment payment = new Payment();
        payment.setReservationId(reservation.id());
        payment.setUserId(userId);
        payment.setAmount(reservation.totalFare());
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setTransactionId("TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        payment.setPaymentMethod(request.paymentMethod().toUpperCase());
        payment.setCreatedAt(RailwayTime.now());
        payment.setUpdatedAt(payment.getCreatedAt());
        paymentRepository.save(payment);

        log.info("Payment {} initiated for reservation {} amount {}", payment.getTransactionId(),
                payment.getReservationId(), payment.getAmount());
        return toResponse(payment, "Redirect the user to the payment gateway. The gateway will call "
                + "POST /api/payments/callback with this transactionId.");
    }

    /** Called by the gateway. Safe to call twice with the same result. */
    public PaymentResponse handleCallback(CallbackRequest request) {
        Payment payment = paymentRepository.findByTransactionId(request.transactionId())
                .orElseThrow(() -> ApiException.notFound("Transaction not found: " + request.transactionId()));
        PaymentStatus outcome = "SUCCESS".equalsIgnoreCase(request.status()) ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;

        if (payment.getStatus() == PaymentStatus.INITIATED) {
            payment.setStatus(outcome);
            payment.setUpdatedAt(RailwayTime.now());
            paymentRepository.save(payment);
            log.info("Payment {} marked {}", payment.getTransactionId(), outcome);
        } else if (payment.getStatus() != outcome) {
            throw ApiException.conflict("Transaction " + payment.getTransactionId() + " is already " + payment.getStatus());
        }

        // Tell reservation-service (it handles repeated notifications safely).
        reservationClient.notifyPaymentResult(payment.getReservationId(),
                new PaymentResultRequest(payment.getId(), payment.getTransactionId(), outcome.name()));
        return toResponse(payment, null);
    }

    public PaymentResponse get(Long paymentId, Long userId, boolean admin) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Payment not found with id " + paymentId));
        if (!admin && !payment.getUserId().equals(userId)) {
            throw ApiException.forbidden("This payment belongs to another user");
        }
        return toResponse(payment, null);
    }

    /**
     * Called by reservation-service when a ticket (or some of its passengers) is cancelled.
     * request.amount() is the TOTAL refunded so far, so we SET it instead of adding to it - calling this
     * twice with the same request therefore changes nothing.
     * Status: REFUNDED when the whole ticket is cancelled, PARTIALLY_REFUNDED when passengers remain.
     */
    public void refund(RefundRequest request) {
        Payment payment = paymentRepository.findFirstByReservationIdAndStatusIn(request.reservationId(),
                        List.of(PaymentStatus.SUCCESS, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED))
                .orElseThrow(() -> ApiException.notFound(
                        "No successful payment found for reservation " + request.reservationId()));
        if (request.amount().compareTo(payment.getAmount()) > 0) {
            throw ApiException.badRequest("Refund cannot be more than the amount paid");
        }
        payment.setRefundAmount(request.amount());
        payment.setStatus(request.fullCancellation() ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        payment.setUpdatedAt(RailwayTime.now());
        paymentRepository.save(payment);
        log.info("Total refund for reservation {} is now {} ({})", request.reservationId(),
                request.amount(), payment.getStatus());
    }

    private PaymentResponse toResponse(Payment p, String message) {
        return new PaymentResponse(p.getId(), p.getReservationId(), p.getTransactionId(), p.getAmount(),
                p.getStatus(), p.getPaymentMethod(), p.getRefundAmount(), message);
    }
}
