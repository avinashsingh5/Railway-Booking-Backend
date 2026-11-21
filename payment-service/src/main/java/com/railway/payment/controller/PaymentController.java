package com.railway.payment.controller;

import com.railway.payment.security.AuthContext;
import com.railway.payment.dto.CallbackRequest;
import com.railway.payment.dto.PaymentRequest;
import com.railway.payment.dto.PaymentResponse;
import com.railway.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** Step 1: start a payment for a PENDING_PAYMENT reservation. */
    @PostMapping
    public ResponseEntity<PaymentResponse> initiate(@Valid @RequestBody PaymentRequest body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.initiate(AuthContext.userId(request), body));
    }

    /**
     * Step 2: the gateway reports the result. Public (a real gateway has no JWT);
     * in production you would verify the gateway's signature here.
     */
    @PostMapping("/callback")
    public PaymentResponse callback(@Valid @RequestBody CallbackRequest body) {
        return paymentService.handleCallback(body);
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable("id") Long id, HttpServletRequest request) {
        return paymentService.get(id, AuthContext.userId(request), AuthContext.isAdmin(request));
    }
}
