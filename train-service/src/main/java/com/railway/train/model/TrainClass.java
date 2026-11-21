package com.railway.train.model;

import com.railway.train.model.TravelClass;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** A coach class on a train, with its seat count and per-km fare. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class TrainClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "train_id")
    private Train train;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TravelClass travelClass;

    @Column(nullable = false)
    private int totalSeats;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal farePerKm;
}
