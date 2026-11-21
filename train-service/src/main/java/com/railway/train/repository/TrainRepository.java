package com.railway.train.repository;

import com.railway.train.model.Train;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TrainRepository extends JpaRepository<Train, Long> {

    boolean existsByTrainNumber(String trainNumber);

    // Trains that stop at both stations with the boarding station BEFORE the destination station
    @Query("select distinct t from Train t join t.stops s1 join t.stops s2 "
            + "where s1.stationCode = :source and s2.stationCode = :destination "
            + "and s1.stopSequence < s2.stopSequence")
    List<Train> findTrainsBetween(@Param("source") String source, @Param("destination") String destination);
}
