package com.railway.payment.controller;

import com.railway.payment.dto.RefundRequest;
import com.railway.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/internal/payments")
public class InternalPaymentController {

    private final PaymentService paymentService;

    public InternalPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/refund")
    public ResponseEntity<Void> refund(@RequestBody RefundRequest request) {
        paymentService.refund(request);
        return ResponseEntity.noContent().build();
    }
}
