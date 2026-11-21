package com.railway.reservation.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Passenger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @Column(nullable = false)
    private String name;

    private int age;

    private String gender;

    private int seatNumber;

    /** Rows created before partial cancellation existed have NULL here - that means CONFIRMED. */
    @Enumerated(EnumType.STRING)
    private PassengerStatus status = PassengerStatus.CONFIRMED;

    public boolean isCancelled() {
        return status == PassengerStatus.CANCELLED;
    }

    public PassengerStatus effectiveStatus() {
        return status == null ? PassengerStatus.CONFIRMED : status;
    }
}
