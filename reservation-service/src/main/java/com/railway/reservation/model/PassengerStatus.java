package com.railway.reservation.model;

public enum PassengerStatus {
    CONFIRMED,
    CANCELLED   // cancelled through a partial (or full) cancellation; the seat has been released
}
