package com.railway.train.controller;

import com.railway.train.model.TravelClass;
import com.railway.train.dto.TrainDetailsResponse;
import com.railway.train.dto.TrainSearchResult;
import com.railway.train.service.TrainService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Public endpoints: anyone can search trains and check availability. */
@RestController
@RequestMapping("/api/trains")
public class TrainController {

    private final TrainService trainService;

    public TrainController(TrainService trainService) {
        this.trainService = trainService;
    }

    /** e.g. GET /api/trains?source=ST&destination=KOTA&date=2026-11-10&travelClass=SL  (any two stops work) */
    @GetMapping
    public List<TrainSearchResult> search(
            @RequestParam("source") String source,
            @RequestParam("destination") String destination,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "travelClass", required = false) TravelClass travelClass) {
        return trainService.search(source, destination, date, travelClass);
    }

    @GetMapping("/{id}")
    public TrainDetailsResponse details(@PathVariable("id") Long id) {
        return trainService.getTrain(id);
    }

    @GetMapping("/{id}/availability")
    public TrainSearchResult availability(
            @PathVariable("id") Long id,
            @RequestParam("source") String source,
            @RequestParam("destination") String destination,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "travelClass", required = false) TravelClass travelClass) {
        return trainService.availability(id, source, destination, date, travelClass);
    }
}
