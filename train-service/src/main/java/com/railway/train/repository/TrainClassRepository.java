package com.railway.train.repository;

import com.railway.train.model.TravelClass;
import com.railway.train.model.TrainClass;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TrainClassRepository extends JpaRepository<TrainClass, Long> {

    /**
     locking the class row till the transaction ends so two people booking the same
     train/class at the same moment are handled one after the other
    **/

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TrainClass c where c.train.id = :trainId and c.travelClass = :travelClass")
    Optional<TrainClass> findForUpdate(@Param("trainId") Long trainId, @Param("travelClass") TravelClass travelClass);


}
