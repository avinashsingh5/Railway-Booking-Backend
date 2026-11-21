package com.railway.train.repository;

import com.railway.train.model.TravelClass;
import com.railway.train.model.SeatAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SeatAllocationRepository extends JpaRepository<SeatAllocation, Long> {


    // find occupied seats
    @Query("select distinct a.seatNumber from SeatAllocation a "
            + "where a.trainId = :trainId and a.runDate = :runDate and a.travelClass = :travelClass "
            + "and a.fromSeq < :toSeq and a.toSeq > :fromSeq")
    List<Integer> findOccupiedSeats(@Param("trainId") Long trainId,
                                    @Param("runDate") LocalDate runDate,
                                    @Param("travelClass") TravelClass travelClass,
                                    @Param("fromSeq") int fromSeq,
                                    @Param("toSeq") int toSeq);

    // delete all records if reservation is canceled completely
    @Modifying
    @Query("delete from SeatAllocation a where a.holdRef = :holdRef")
    int deleteByHoldRef(@Param("holdRef") String holdRef);



    //Frees only some seats of a booking (partial cancellation)
    @Modifying
    @Query("delete from SeatAllocation a where a.holdRef = :holdRef and a.seatNumber in :seatNumbers")
    int deleteByHoldRefAndSeatNumbers(@Param("holdRef") String holdRef, @Param("seatNumbers") List<Integer> seatNumbers);



    // Number of bookings  holding seats on this train.

    @Query("select count(distinct a.holdRef) from SeatAllocation a where a.trainId = :trainId and a.runDate >= :since")
    long countActiveBookings(@Param("trainId") Long trainId, @Param("since") LocalDate since);

    @Modifying
    @Query("delete from SeatAllocation a where a.trainId = :trainId")
    int deleteByTrainId(@Param("trainId") Long trainId);
}
