package com.railway.reservation.messaging;

/** Event types published after a booking action. Notification-service has been removed;
 *  this enum is retained so service-layer code compiles without modification. */
public enum BookingEventType {
    BOOKING_CONFIRMED,
    PAYMENT_FAILED,
    BOOKING_CANCELLED,
    BOOKING_PARTIALLY_CANCELLED
}

