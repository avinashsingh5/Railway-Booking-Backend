package com.railway.reservation.service;

import com.railway.reservation.client.TrainClient;
import com.railway.reservation.dto.*;
import com.railway.reservation.error.ApiException;
import com.railway.reservation.messaging.BookingEventType;
import com.railway.reservation.messaging.EventPublisher;
import com.railway.reservation.model.Passenger;
import com.railway.reservation.model.Reservation;
import com.railway.reservation.model.ReservationStatus;
import com.railway.reservation.repository.ReservationRepository;
import com.railway.reservation.util.RailwayTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Note: create() and handlePaymentResult() are NOT @Transactional on purpose - they call other
 * services over HTTP, and we do not want to keep a database transaction open during those calls.
 */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository reservationRepository;
    private final TrainClient trainClient;
    private final EventPublisher eventPublisher;
    private final long holdMinutes;

    public ReservationService(ReservationRepository reservationRepository,
                              TrainClient trainClient,
                              EventPublisher eventPublisher,
                              @Value("${reservation.payment-hold-minutes:15}") long holdMinutes) {
        this.reservationRepository = reservationRepository;
        this.trainClient = trainClient;
        this.eventPublisher = eventPublisher;
        this.holdMinutes = holdMinutes;
    }


    // 1. Create a reservation: hold seats, store it as PENDING_PAYMENT

    public ReservationResponse create(Long userId, String email, ReservationRequest request) {
        String holdRef = UUID.randomUUID().toString();

        // train-service validates the stations, checks availability for THIS segment,
        // picks the seats and calculates the fare.
        AllocationResponse allocation = trainClient.allocate(new AllocationRequest(
                holdRef, request.trainId(), request.journeyDate(), request.travelClass(),
                request.boardingStation(), request.destinationStation(), request.passengers().size()));

        Reservation reservation = new Reservation();
        reservation.setHoldRef(holdRef);
        reservation.setUserId(userId);
        reservation.setContactEmail(email);
        reservation.setTrainId(allocation.trainId());
        reservation.setTrainNumber(allocation.trainNumber());
        reservation.setTrainName(allocation.trainName());
        reservation.setBoardingStation(allocation.boardingStation());
        reservation.setBoardingStationName(allocation.boardingStationName());
        reservation.setDestinationStation(allocation.destinationStation());
        reservation.setDestinationStationName(allocation.destinationStationName());
        reservation.setRunDate(allocation.runDate());
        reservation.setBoardingDate(allocation.boardingDate());
        reservation.setDepartureTime(allocation.departureTime());
        reservation.setArrivalDate(allocation.arrivalDate());
        reservation.setArrivalTime(allocation.arrivalTime());
        reservation.setDistanceKm(allocation.distanceKm());
        reservation.setTravelClass(request.travelClass());
        reservation.setTotalFare(allocation.totalFare());
        reservation.setStatus(ReservationStatus.PENDING_PAYMENT);
        reservation.setCreatedAt(RailwayTime.now());

        List<ReservationRequest.PassengerRequest> people = request.passengers();
        for (int i = 0; i < people.size(); i++) {
            Passenger passenger = new Passenger();
            passenger.setReservation(reservation);
            passenger.setName(people.get(i).name().trim());
            passenger.setAge(people.get(i).age());
            passenger.setGender(people.get(i).gender().toUpperCase());
            passenger.setSeatNumber(allocation.seatNumbers().get(i));
            reservation.getPassengers().add(passenger);
        }

        try {
            reservationRepository.save(reservation);
        } catch (RuntimeException ex) {
            trainClient.release(holdRef); // do not leave seats blocked if we could not store the reservation
            throw ex;
        }
        log.info("Reservation {} created for user {} ({} -> {}), waiting for payment",
                reservation.getId(), userId, reservation.getBoardingStation(), reservation.getDestinationStation());
        return toResponse(reservation);
    }


    // 2. Payment result arrives from payment-service

    public void handlePaymentResult(Long reservationId, PaymentResultRequest result) {
        Reservation reservation = findById(reservationId);
        boolean success = "SUCCESS".equalsIgnoreCase(result.status());

        // The gateway may call us twice - handle repeats quietly.
        if (success && reservation.getStatus().isIssued()) {
            return;
        }
        if (!success && reservation.getStatus() == ReservationStatus.CANCELLED) {
            return;
        }
        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            throw ApiException.conflict("Reservation " + reservationId + " is " + reservation.getStatus()
                    + " and can no longer accept a payment result");
        }

        reservation.setPaymentId(result.paymentId());
        if (success) {
            if (reservation.getPnr() == null) {
                reservation.setPnr(generateUniquePnr());
            }
            reservation.setStatus(ReservationStatus.CONFIRMED);
            reservationRepository.save(reservation);
            log.info("Reservation {} CONFIRMED with PNR {}", reservationId, reservation.getPnr());
            eventPublisher.publish(BookingEventType.BOOKING_CONFIRMED, reservation, null);
        } else {
            trainClient.release(reservation.getHoldRef());
            reservation.setStatus(ReservationStatus.CANCELLED);
            reservationRepository.save(reservation);
            log.info("Reservation {} CANCELLED because payment failed", reservationId);
            eventPublisher.publish(BookingEventType.PAYMENT_FAILED, reservation, null);
        }
    }


    // 3. Reading

    @Transactional(readOnly = true)
    public ReservationResponse get(Long id, Long userId, boolean admin) {
        return toResponse(checkAccess(findById(id), userId, admin));
    }

    @Transactional(readOnly = true)
    public ReservationResponse getByPnr(String pnr, Long userId, boolean admin) {
        Reservation reservation = reservationRepository.findByPnr(pnr.trim())
                .orElseThrow(() -> ApiException.notFound("No ticket found for PNR " + pnr));
        return toResponse(checkAccess(reservation, userId, admin));
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> listForUser(Long userId) {
        return reservationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> listAll(ReservationStatus status) {
        List<Reservation> all = status == null
                ? reservationRepository.findAllByOrderByCreatedAtDesc()
                : reservationRepository.findByStatusOrderByCreatedAtDesc(status);
        return all.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ReservationSummary summary(Long id) {
        Reservation r = findById(id);
        return new ReservationSummary(r.getId(), r.getUserId(), r.getStatus().name(), r.getTotalFare());
    }

    // 4. Housekeeping: free seats of reservations nobody paid for

    @Scheduled(fixedDelay = 60000)
    public void expireUnpaidReservations() {
        LocalDateTime cutoff = RailwayTime.now().minusMinutes(holdMinutes);
        for (Reservation reservation : reservationRepository.findByStatusAndCreatedAtBefore(ReservationStatus.PENDING_PAYMENT, cutoff)) {
            try {
                trainClient.release(reservation.getHoldRef());
                reservation.setStatus(ReservationStatus.EXPIRED);
                reservationRepository.save(reservation);
                log.info("Reservation {} expired (no payment within {} minutes)", reservation.getId(), holdMinutes);
            } catch (RuntimeException ex) {
                log.warn("Could not expire reservation {}: {}", reservation.getId(), ex.getMessage());
            }
        }
    }


    // helpers


    /**
     * 10-digit numeric PNR, generated only when a reservation becomes CONFIRMED.
     * existsByPnr() avoids clashes; the UNIQUE constraint on the pnr column is the final safety net.
     */
    private String generateUniquePnr() {
        String candidate;
        do {
            candidate = String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L));
        } while (reservationRepository.existsByPnr(candidate));
        return candidate;
    }

    private Reservation findById(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Reservation not found with id " + id));
    }

    private Reservation checkAccess(Reservation reservation, Long userId, boolean admin) {
        if (!admin && !reservation.getUserId().equals(userId)) {
            throw ApiException.forbidden("This reservation belongs to another user");
        }
        return reservation;
    }

    private ReservationResponse toResponse(Reservation r) {
        List<ReservationResponse.PassengerInfo> passengers = r.getPassengers().stream()
                .map(p -> new ReservationResponse.PassengerInfo(p.getId(), p.getName(), p.getAge(), p.getGender(),
                        p.getSeatNumber(), p.effectiveStatus()))
                .toList();
        LocalDateTime dueBy = r.getStatus() == ReservationStatus.PENDING_PAYMENT
                ? r.getCreatedAt().plusMinutes(holdMinutes) : null;
        return new ReservationResponse(
                r.getId(), r.getPnr(), r.getStatus(),
                r.getTrainNumber(), r.getTrainName(),
                r.getBoardingStation(), r.getBoardingStationName(),
                r.getDestinationStation(), r.getDestinationStationName(),
                r.getBoardingDate(), r.getDepartureTime(), r.getArrivalDate(), r.getArrivalTime(),
                r.getTravelClass(), r.getDistanceKm(), r.getTotalFare(), r.remainingFare(),
                passengers, r.getCreatedAt(), dueBy);
    }
}
