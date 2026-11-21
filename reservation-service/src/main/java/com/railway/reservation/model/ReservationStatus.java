package com.railway.reservation.model;

public enum ReservationStatus {
    PENDING_PAYMENT,     // seats are held, waiting for payment
    CONFIRMED,           // paid, PNR issued, every passenger travelling
    PARTIALLY_CANCELLED, // paid, but some passengers were cancelled - the others still travel on the same PNR
    CANCELLED,           // cancelled by the user (every passenger), or payment failed
    EXPIRED;             // payment not completed in time, seats released

    /** True while a valid ticket exists: CONFIRMED or PARTIALLY_CANCELLED. */
    public boolean isIssued() {
        return this == CONFIRMED || this == PARTIALLY_CANCELLED;
    }
}
