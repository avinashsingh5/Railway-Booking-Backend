package com.railway.reservation.controller;

import com.railway.reservation.dto.PaymentResultRequest;
import com.railway.reservation.dto.ReservationSummary;
import com.railway.reservation.service.ReservationService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Only payment-service calls these (needs the X-Internal-Key header). */
@Hidden
@RestController
@RequestMapping("/internal/reservations")
public class InternalReservationController {

    private final ReservationService reservationService;

    public InternalReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/{id}")
    public ReservationSummary summary(@PathVariable("id") Long id) {
        return reservationService.summary(id);
    }

    @PostMapping("/{id}/payment-result")
    public ResponseEntity<Void> paymentResult(@PathVariable("id") Long id, @RequestBody PaymentResultRequest body) {
        reservationService.handlePaymentResult(id, body);
        return ResponseEntity.noContent().build();
    }
}
