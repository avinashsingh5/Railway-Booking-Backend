package com.railway.train.model;

import com.railway.train.model.TravelClass;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * "Seat number N of this train/date/class is taken from stop fromSeq up to stop toSeq".
 * Two bookings can share a seat as long as their segments do not overlap.
 */
@Entity
@Getter
@NoArgsConstructor
public class SeatAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long trainId;

    /** Date the train started from its origin station. */
    private LocalDate runDate;

    @Enumerated(EnumType.STRING)
    private TravelClass travelClass;

    private int seatNumber;

    /** Sequence of the boarding stop (inclusive). */
    private int fromSeq;

    /** Sequence of the destination stop (the seat is free again from here). */
    private int toSeq;

    /** Identifies the reservation that holds this seat. */
    private String holdRef;

    public SeatAllocation(Long trainId, LocalDate runDate, TravelClass travelClass,
                          int seatNumber, int fromSeq, int toSeq, String holdRef) {
        this.trainId = trainId;
        this.runDate = runDate;
        this.travelClass = travelClass;
        this.seatNumber = seatNumber;
        this.fromSeq = fromSeq;
        this.toSeq = toSeq;
        this.holdRef = holdRef;
    }
}
