package com.railway.train.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Train {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String trainNumber;

    @Column(nullable = false)
    private String name;

    /** Station code of the first stop (origin). */
    @Column(nullable = false)
    private String source;

    /** Station code of the last stop (terminus). */
    @Column(nullable = false)
    private String destination;

    /** Ordered list of stops - this is what makes intermediate-station booking possible. */
    @OneToMany(mappedBy = "train", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stopSequence ASC")
    private List<TrainStop> stops = new ArrayList<>();

    @OneToMany(mappedBy = "train", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TrainClass> classes = new ArrayList<>();

    /**
     * Days of the week the train LEAVES ITS ORIGIN station. Stored in its own table
     * train_running_day (train_id, day_of_week). An empty set (old data) is treated as "runs daily".
     */
    @ElementCollection
    @CollectionTable(name = "train_running_day", joinColumns = @JoinColumn(name = "train_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private Set<DayOfWeek> runningDays = new HashSet<>();
}
