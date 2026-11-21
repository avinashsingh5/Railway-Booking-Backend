package com.railway.train.controller;

import com.railway.train.dto.AllocationRequest;
import com.railway.train.dto.AllocationResponse;
import com.railway.train.dto.ReleaseSeatsRequest;
import com.railway.train.service.TrainService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Only other services call this (needs the X-Internal-Key header). */
@Hidden
@RestController
@RequestMapping("/internal/trains")
public class InternalSeatController {

    private final TrainService trainService;

    public InternalSeatController(TrainService trainService) {
        this.trainService = trainService;
    }

    @PostMapping("/allocate")
    public AllocationResponse allocate(@RequestBody AllocationRequest request) {
        return trainService.allocate(request);
    }

    @PostMapping("/release-seats")
    public ResponseEntity<Void> releaseSeats(@RequestBody ReleaseSeatsRequest request) {
        trainService.releaseSeats(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/release/{holdRef}")
    public ResponseEntity<Void> release(@PathVariable("holdRef") String holdRef) {
        trainService.release(holdRef);
        return ResponseEntity.noContent().build();
    }
}
