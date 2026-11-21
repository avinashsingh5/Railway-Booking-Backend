package com.railway.reservation.model;

import com.railway.reservation.model.TravelClass;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Key under which train-service holds the seats of this reservation. */
    @Column(nullable = false, unique = true)
    private String holdRef;

    /** 10-digit PNR, filled in when payment succeeds (NULL while PENDING_PAYMENT). */
    @Column(name = "pnr", unique = true, length = 10)
    private String pnr;

    @Column(nullable = false)
    private Long userId;

    private String contactEmail;

    // ---- journey snapshot (copied from train-service at booking time) ----
    private Long trainId;
    private String trainNumber;
    private String trainName;
    private String boardingStation;
    private String boardingStationName;
    private String destinationStation;
    private String destinationStationName;
    private LocalDate runDate;
    private LocalDate boardingDate;
    private LocalTime departureTime;
    private LocalDate arrivalDate;
    private LocalTime arrivalTime;
    private int distanceKm;

    @Enumerated(EnumType.STRING)
    private TravelClass travelClass;

    /** Amount paid for the booking. Never changes after booking, so reports keep the original value. */
    @Column(precision = 10, scale = 2)
    private BigDecimal totalFare;

    /** Value of the passengers cancelled so far (NULL on old rows = nothing cancelled). */
    @Column(precision = 10, scale = 2)
    private BigDecimal cancelledFare;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    private Long paymentId;

    private LocalDateTime createdAt;

    /** What the passengers still travelling are worth: totalFare - cancelledFare. */
    public BigDecimal remainingFare() {
        return totalFare.subtract(cancelledFare == null ? BigDecimal.ZERO : cancelledFare);
    }

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Passenger> passengers = new ArrayList<>();
}
