package com.railway.train.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/** One station on a train's route. stopSequence 1 = origin, N = terminus. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class TrainStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "train_id")
    private Train train;

    @Column(nullable = false)
    private int stopSequence;

    @Column(nullable = false)
    private String stationCode;

    @Column(nullable = false)
    private String stationName;

    /** null for the origin station. */
    private LocalTime arrivalTime;

    /** null for the terminus station. */
    private LocalTime departureTime;

    /** 0 = same day the train left its origin, 1 = next day, ... */
    @Column(nullable = false)
    private int dayOffset;

    /** Distance from the origin station in km - fares are calculated from this. */
    @Column(nullable = false)
    private int distanceKm;
}
