package com.railway.reservation.service;

import com.railway.reservation.client.PaymentClient;
import com.railway.reservation.client.TrainClient;
import com.railway.reservation.dto.CancellationResponse;
import com.railway.reservation.dto.RefundRequest;
import com.railway.reservation.dto.ReleaseSeatsRequest;
import com.railway.reservation.error.ApiException;
import com.railway.reservation.messaging.BookingEventType;
import com.railway.reservation.messaging.EventPublisher;
import com.railway.reservation.model.Cancellation;
import com.railway.reservation.model.Passenger;
import com.railway.reservation.model.PassengerStatus;
import com.railway.reservation.model.Reservation;
import com.railway.reservation.model.ReservationStatus;
import com.railway.reservation.repository.CancellationRepository;
import com.railway.reservation.repository.ReservationRepository;
import com.railway.reservation.util.RailwayTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


@Service
public class CancellationService {

    private static final Logger log = LoggerFactory.getLogger(CancellationService.class);

    private final ReservationRepository reservationRepository;
    private final CancellationRepository cancellationRepository;
    private final TrainClient trainClient;
    private final PaymentClient paymentClient;
    private final EventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public CancellationService(ReservationRepository reservationRepository,
                               CancellationRepository cancellationRepository,
                               TrainClient trainClient,
                               PaymentClient paymentClient,
                               EventPublisher eventPublisher,
                               TransactionTemplate transactionTemplate) {
        this.reservationRepository = reservationRepository;
        this.cancellationRepository = cancellationRepository;
        this.trainClient = trainClient;
        this.paymentClient = paymentClient;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
    }

    /** passengerIds == null means "cancel the whole ticket". */
    public CancellationResponse cancel(Long userId, boolean admin, String pnr, List<Long> passengerIds) {
        Outcome outcome = transactionTemplate.execute(status -> doCancel(userId, admin, pnr, passengerIds));
        // the email event is sent only after the database change is committed
        eventPublisher.publish(outcome.eventType(), outcome.reservation(), outcome.refund(), outcome.cancelledPassengers());
        return outcome.response();
    }

    private Outcome doCancel(Long userId, boolean admin, String pnr, List<Long> passengerIds) {
        Reservation reservation = reservationRepository.findByPnrForUpdate(pnr.trim())
                .orElseThrow(() -> ApiException.notFound("No ticket found for PNR " + pnr));

        if (!admin && !reservation.getUserId().equals(userId)) {
            throw ApiException.forbidden("This ticket belongs to another user");
        }
        if (!reservation.getStatus().isIssued()) { // CONFIRMED or PARTIALLY_CANCELLED
            throw ApiException.conflict("Only confirmed tickets can be cancelled (this ticket is "
                    + reservation.getStatus() + ")");
        }

        LocalDateTime now = RailwayTime.now();
        LocalDateTime departure = LocalDateTime.of(reservation.getBoardingDate(), reservation.getDepartureTime());
        if (!departure.isAfter(now)) {
            throw ApiException.badRequest("The train has already departed, so this ticket cannot be cancelled");
        }

        // ---- who is cancelled?
        List<Passenger> active = reservation.getPassengers().stream().filter(p -> !p.isCancelled()).toList();
        List<Passenger> toCancel = selectPassengers(reservation, active, passengerIds);
        boolean full = toCancel.size() == active.size();

        // ---- how much is refunded?
        // Cancelled share of what is STILL LEFT of the fare (earlier partial cancellations already took theirs).
        // The last passenger gets exactly what is left, so the shares always add up to the amount paid.
        BigDecimal remainingBefore = reservation.remainingFare();
        BigDecimal share = full
                ? remainingBefore
                : remainingBefore
                        .multiply(BigDecimal.valueOf(toCancel.size()))
                        .divide(BigDecimal.valueOf(active.size()), 2, RoundingMode.HALF_UP);

        long hoursLeft = Duration.between(now, departure).toHours();
        int refundPercent = hoursLeft >= 24 ? 90 : hoursLeft >= 4 ? 50 : 0;
        BigDecimal refund = share
                .multiply(BigDecimal.valueOf(refundPercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // ---- downstream calls (both safe to repeat). Refund first, seats second - see the class comment.
        // payment-service stores the TOTAL refunded so far, so repeating this call changes nothing
        BigDecimal totalRefunded = refund;
        for (Cancellation earlier : cancellationRepository.findByReservationId(reservation.getId())) {
            if (earlier.getRefundAmount() != null) {
                totalRefunded = totalRefunded.add(earlier.getRefundAmount());
            }
        }
        if (totalRefunded.signum() > 0) {
            paymentClient.refund(new RefundRequest(reservation.getId(), totalRefunded, full));
        }

        if (full) {
            trainClient.release(reservation.getHoldRef());
        } else {
            List<Integer> seats = toCancel.stream().map(Passenger::getSeatNumber).toList();
            trainClient.releaseSeats(new ReleaseSeatsRequest(reservation.getHoldRef(), seats));
        }

        // ---- save
        for (Passenger passenger : toCancel) {
            passenger.setStatus(PassengerStatus.CANCELLED);
        }
        BigDecimal remainingAfter = remainingBefore.subtract(share);
        reservation.setCancelledFare(reservation.getTotalFare().subtract(remainingAfter)); // totalFare itself is untouched
        reservation.setStatus(full ? ReservationStatus.CANCELLED : ReservationStatus.PARTIALLY_CANCELLED);
        reservationRepository.save(reservation);

        Cancellation cancellation = new Cancellation();
        cancellation.setReservationId(reservation.getId());
        cancellation.setStatus(refund.signum() > 0 ? "REFUNDED" : "NO_REFUND");
        cancellation.setRefundAmount(refund);
        cancellation.setCancelTime(now);
        cancellation.setType(full ? "FULL" : "PARTIAL");
        cancellation.setPassengerCount(toCancel.size());
        cancellationRepository.save(cancellation);

        log.info("Ticket {} {} cancellation: {} passenger(s), refund {}", reservation.getPnr(),
                full ? "full" : "partial", toCancel.size(), refund);

        List<CancellationResponse.CancelledPassenger> cancelledInfo = toCancel.stream()
                .map(p -> new CancellationResponse.CancelledPassenger(p.getId(), p.getName(), p.getSeatNumber()))
                .toList();
        CancellationResponse response = new CancellationResponse(
                cancellation.getId(), reservation.getPnr(), cancellation.getStatus(), refund, now,
                cancellation.getType(), cancelledInfo,
                active.size() - toCancel.size(),
                remainingAfter);

        return new Outcome(full ? BookingEventType.BOOKING_CANCELLED : BookingEventType.BOOKING_PARTIALLY_CANCELLED,
                reservation, refund, toCancel, response);
    }

    /** null ids = everybody still active; otherwise every id must be an active passenger of THIS ticket. */
    private List<Passenger> selectPassengers(Reservation reservation, List<Passenger> active, List<Long> passengerIds) {
        if (passengerIds == null) {
            return active;
        }
        Set<Long> wanted = new LinkedHashSet<>(passengerIds); // removes duplicates, keeps order
        List<Passenger> selected = new ArrayList<>();
        for (Long id : wanted) {
            Passenger match = null;
            for (Passenger p : reservation.getPassengers()) {
                if (p.getId().equals(id)) {
                    match = p;
                    break;
                }
            }
            if (match == null) {
                throw ApiException.badRequest("Passenger " + id + " is not part of this ticket");
            }
            if (match.isCancelled()) {
                throw ApiException.conflict("Passenger " + match.getName() + " (id " + id + ") is already cancelled");
            }
            selected.add(match);
        }
        return selected;
    }

    private record Outcome(BookingEventType eventType, Reservation reservation, BigDecimal refund,
                           List<Passenger> cancelledPassengers, CancellationResponse response) {
    }
}
