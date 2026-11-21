package com.railway.reservation.controller;

import com.railway.reservation.security.AuthContext;
import com.railway.reservation.dto.ReservationRequest;
import com.railway.reservation.dto.ReservationResponse;
import com.railway.reservation.service.ReservationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody ReservationRequest body, HttpServletRequest request) {
        ReservationResponse response = reservationService.create(
                AuthContext.userId(request), AuthContext.email(request), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my")
    public List<ReservationResponse> myReservations(HttpServletRequest request) {
        return reservationService.listForUser(AuthContext.userId(request));
    }

    @GetMapping("/{id}")
    public ReservationResponse get(@PathVariable("id") Long id, HttpServletRequest request) {
        return reservationService.get(id, AuthContext.userId(request), AuthContext.isAdmin(request));
    }

    /** Ticket status by PNR. */
    @GetMapping("/pnr/{pnr}")
    public ReservationResponse getByPnr(@PathVariable("pnr") String pnr, HttpServletRequest request) {
        return reservationService.getByPnr(pnr, AuthContext.userId(request), AuthContext.isAdmin(request));
    }
}
