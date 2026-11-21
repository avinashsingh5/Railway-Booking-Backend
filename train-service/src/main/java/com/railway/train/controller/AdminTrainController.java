package com.railway.train.controller;

import com.railway.train.dto.CreateTrainRequest;
import com.railway.train.dto.TrainDetailsResponse;
import com.railway.train.service.TrainService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin only - the API gateway blocks /api/admin/** for non-admin tokens. */
@RestController
@RequestMapping("/api/admin/trains")
public class AdminTrainController {

    private static final String CREATE_EXAMPLE = """
            {
              "trainNumber": "12951",
              "name": "Mumbai - Delhi Express",
              "stops": [
                { "stationCode": "MUM", "stationName": "Mumbai Central", "departureTime": "17:00", "dayOffset": 0, "distanceKm": 0 },
                { "stationCode": "ST", "stationName": "Surat", "arrivalTime": "19:45", "departureTime": "19:50", "dayOffset": 0, "distanceKm": 263 },
                { "stationCode": "DEL", "stationName": "New Delhi", "arrivalTime": "08:30", "dayOffset": 1, "distanceKm": 1384 }
              ],
              "classes": [
                { "travelClass": "SL", "totalSeats": 72, "farePerKm": 0.60 },
                { "travelClass": "AC3", "totalSeats": 64, "farePerKm": 1.60 }
              ],
              "runningDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]
            }""";

    private final TrainService trainService;

    public AdminTrainController(TrainService trainService) {
        this.trainService = trainService;
    }

    @Operation(summary = "Create a train",
            description = "Creates a train with its route (stops in order), classes and running days. "
                    + "runningDays is optional: if omitted the train runs every day.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Train created"),
            @ApiResponse(responseCode = "400", description = "Invalid route, classes or running days"),
            @ApiResponse(responseCode = "409", description = "A train with this number already exists")})
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
            examples = @ExampleObject(name = "create", value = CREATE_EXAMPLE)))
    @PostMapping
    public ResponseEntity<TrainDetailsResponse> create(@Valid @RequestBody CreateTrainRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(trainService.createTrain(request));
    }

    @Operation(summary = "Delete a train",
            description = "Deletes the train with its stops, classes and running days. Not allowed (409) while it has "
                    + "upcoming bookings (confirmed or awaiting payment). Past reservations stay readable.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Train deleted"),
            @ApiResponse(responseCode = "404", description = "Train not found"),
            @ApiResponse(responseCode = "409", description = "Train has upcoming bookings")})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        trainService.deleteTrain(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List all trains")
    @GetMapping
    public List<TrainDetailsResponse> list() {
        return trainService.listTrains();
    }
}
