package com.railway.reservation.messaging;

import com.railway.reservation.model.Passenger;
import com.railway.reservation.model.Reservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * No-op event publisher. The notification-service has been removed from the system;
 * this stub retains the same API so service-layer code requires no changes.
 * Events are logged at INFO level for observability.
 */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    /** Publishes an event for all active (non-cancelled) passengers on the reservation. */
    public void publish(BookingEventType type, Reservation reservation, BigDecimal refundAmount) {
        log.info("[EVENT] {} | reservation={} pnr={}", type, reservation.getId(), reservation.getPnr());
    }

    /** Publishes an event for a specific subset of passengers (e.g. those just cancelled). */
    public void publish(BookingEventType type, Reservation reservation, BigDecimal refundAmount,
                        List<Passenger> listedPassengers) {
        log.info("[EVENT] {} | reservation={} pnr={} passengers={}",
                type, reservation.getId(), reservation.getPnr(), listedPassengers.size());
    }
}

