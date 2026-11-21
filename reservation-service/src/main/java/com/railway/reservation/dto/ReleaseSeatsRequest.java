package com.railway.reservation.dto;

import java.util.List;

/** reservation-service -> train-service: free only these seats of a booking (partial cancellation). */
public record ReleaseSeatsRequest(String holdRef, List<Integer> seatNumbers) {
}
