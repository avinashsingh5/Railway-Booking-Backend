package com.railway.reservation.dto;

import com.railway.reservation.model.TravelClass;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

/**
 * boardingStation and destinationStation can be ANY two stops on the train's route -
 * that is the intermediate station booking feature.
 */
public record ReservationRequest(
        @NotNull Long trainId,
        @NotNull LocalDate journeyDate,
        @NotNull TravelClass travelClass,
        @NotBlank String boardingStation,
        @NotBlank String destinationStation,
        @NotNull @Size(min = 1, max = 6, message = "must have 1 to 6 passengers") @Valid List<PassengerRequest> passengers) {

    public record PassengerRequest(
            @NotBlank String name,
            @NotNull @Min(1) @Max(120) Integer age,
            @NotBlank @Pattern(regexp = "(?i)MALE|FEMALE|OTHER", message = "must be MALE, FEMALE or OTHER") String gender) {
    }
}
