package com.railway.reservation.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Cancellation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reservationId;

    /** REFUNDED or NO_REFUND. */
    private String status;

    @Column(precision = 10, scale = 2)
    private BigDecimal refundAmount;

    private LocalDateTime cancelTime;

    /** FULL or PARTIAL (NULL on old rows = FULL). */
    private String type;

    /** How many passengers were cancelled by this cancellation. */
    private Integer passengerCount;
}
