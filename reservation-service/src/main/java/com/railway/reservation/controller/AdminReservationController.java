package com.railway.reservation.controller;

import com.railway.reservation.dto.ReservationResponse;
import com.railway.reservation.model.ReservationStatus;
import com.railway.reservation.service.ReservationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/api/admin/reservations")
public class AdminReservationController {

    private final ReservationService reservationService;

    public AdminReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping
    public List<ReservationResponse> list(@RequestParam(value = "status", required = false) ReservationStatus status) {
        return reservationService.listAll(status);
    }
}
