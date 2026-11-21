package com.railway.reservation.controller;

import com.railway.reservation.security.AuthContext;
import com.railway.reservation.dto.CancellationRequest;
import com.railway.reservation.dto.CancellationResponse;
import com.railway.reservation.service.CancellationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cancellations")
public class CancellationController {

    private final CancellationService cancellationService;

    public CancellationController(CancellationService cancellationService) {
        this.cancellationService = cancellationService;
    }

    @Operation(summary = "Cancel a ticket (whole ticket or only some passengers)",
            description = "Without passengerIds the whole ticket is cancelled. With passengerIds only those passengers are "
                    + "cancelled: their seats are released, the ticket (same PNR) stays CONFIRMED for the others and the "
                    + "fare is reduced. Refund = the cancelled passengers' share of the fare: 90% if 24h or more before "
                    + "departure, 50% from 4h to 24h, nothing under 4h. Listing every remaining passenger cancels the whole ticket.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cancelled"),
            @ApiResponse(responseCode = "400", description = "Passenger not on this ticket, or the train has already departed"),
            @ApiResponse(responseCode = "403", description = "Ticket belongs to another user"),
            @ApiResponse(responseCode = "404", description = "No ticket for this PNR"),
            @ApiResponse(responseCode = "409", description = "Ticket is not CONFIRMED, or a passenger is already cancelled")})
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = {
            @ExampleObject(name = "partial", summary = "Cancel two passengers",
                    value = "{ \"pnr\": \"4829173650\", \"passengerIds\": [12, 13] }"),
            @ExampleObject(name = "full", summary = "Cancel the whole ticket",
                    value = "{ \"pnr\": \"4829173650\" }")}))
    @PostMapping
    public ResponseEntity<CancellationResponse> cancel(@Valid @RequestBody CancellationRequest body, HttpServletRequest request) {
        CancellationResponse response = cancellationService.cancel(
                AuthContext.userId(request), AuthContext.isAdmin(request), body.pnr(), body.passengerIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
