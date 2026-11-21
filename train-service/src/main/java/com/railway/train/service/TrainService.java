package com.railway.train.service;

import com.railway.train.dto.*;
import com.railway.train.dto.CreateTrainRequest.ClassRequest;
import com.railway.train.dto.CreateTrainRequest.StopRequest;
import com.railway.train.error.ApiException;
import com.railway.train.model.*;
import com.railway.train.repository.SeatAllocationRepository;
import com.railway.train.repository.TrainClassRepository;
import com.railway.train.repository.TrainRepository;
import com.railway.train.util.RailwayTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class TrainService {

    private static final Logger log = LoggerFactory.getLogger(TrainService.class);

    private final TrainRepository trainRepository;
    private final TrainClassRepository trainClassRepository;
    private final SeatAllocationRepository seatRepository;
    private final BigDecimal minimumFare;

    public TrainService(TrainRepository trainRepository,
                        TrainClassRepository trainClassRepository,
                        SeatAllocationRepository seatRepository,
                        @Value("${fare.minimum:50}") BigDecimal minimumFare) {
        this.trainRepository = trainRepository;
        this.trainClassRepository = trainClassRepository;
        this.seatRepository = seatRepository;
        this.minimumFare = minimumFare;
    }


    // Admin: create / list trains


    @Transactional
    public TrainDetailsResponse createTrain(CreateTrainRequest request) {
        String trainNumber = request.trainNumber().trim();
        if (trainRepository.existsByTrainNumber(trainNumber)) {
            throw ApiException.conflict("Train " + trainNumber + " already exists");
        }
        validateStops(request.stops());
        validateClasses(request.classes());

        Train train = new Train();
        train.setTrainNumber(trainNumber);
        train.setName(request.name().trim());
        replaceStops(train, request.stops());

        for (ClassRequest c : request.classes()) {
            TrainClass trainClass = new TrainClass();
            trainClass.setTrain(train);
            trainClass.setTravelClass(c.travelClass());
            trainClass.setTotalSeats(c.totalSeats());
            trainClass.setFarePerKm(c.farePerKm());
            train.getClasses().add(trainClass);
        }

        // runningDays is optional on create: no list = the train runs every day
        train.getRunningDays().addAll(request.runningDays() == null
                ? List.of(DayOfWeek.values()) : request.runningDays());

        trainRepository.save(train);
        log.info("Created train {} with {} stops", trainNumber, request.stops().size());
        return toDetails(train);
    }

    /**
     * deletes a train.
     * A train cannot be deleted if passengers have future booking on it.
     * we check the seat allocation table to see if any active booking exists
     * If there is active booking return 409 conflict
     * **/
    @Transactional
    public void deleteTrain(Long id) {
        Train train = findTrain(id);
        lockClasses(train);
        long upcoming = seatRepository.countActiveBookings(id, activeSince(train));
        if (upcoming > 0) {
            throw ApiException.conflict("Train " + train.getTrainNumber() + " cannot be deleted: it has " + upcoming
                    + " upcoming booking(s) (confirmed or awaiting payment). Cancel them first.");
        }
        seatRepository.deleteByTrainId(id);
        trainRepository.delete(train); // cascades to stops, classes and running days
        log.info("Deleted train {}", train.getTrainNumber());
    }

    @Transactional(readOnly = true)
    public List<TrainDetailsResponse> listTrains() {
        return trainRepository.findAll().stream().map(this::toDetails).toList();
    }

    @Transactional(readOnly = true)
    public TrainDetailsResponse getTrain(Long id) {
        return toDetails(findTrain(id));
    }


    // Search + availability


    @Transactional(readOnly = true)
    public List<TrainSearchResult> search(String source, String destination, LocalDate date, TravelClass travelClass) {
        String src = code(source);
        String dst = code(destination);
        if (src.equals(dst)) {
            throw ApiException.badRequest("Source and destination must be different");
        }
        validateDate(date);

        LocalDateTime now = RailwayTime.now();
        List<TrainSearchResult> results = new ArrayList<>();
        for (Train train : trainRepository.findTrainsBetween(src, dst)) {
            TrainStop[] journey = resolveJourney(train, src, dst);
            if (!runsOn(train, date.minusDays(journey[0].getDayOffset()))) {
                continue; // train does not run on that day
            }
            TrainSearchResult result = buildResult(train, journey[0], journey[1], date, travelClass);
            if (result.classes().isEmpty()) {
                continue; // requested class does not exist on this train
            }
            if (LocalDateTime.of(result.boardingDate(), result.departureTime()).isBefore(now)) {
                continue; // already left the boarding station
            }
            results.add(result);
        }
        results.sort(Comparator.comparing((TrainSearchResult r) -> LocalDateTime.of(r.boardingDate(), r.departureTime())));
        return results;
    }

    @Transactional(readOnly = true)
    public TrainSearchResult availability(Long trainId, String source, String destination,
                                          LocalDate date, TravelClass travelClass) {
        Train train = findTrain(trainId);
        TrainStop[] journey = resolveJourney(train, code(source), code(destination));
        validateDate(date);
        requireRunning(train, journey[0], date);
        return buildResult(train, journey[0], journey[1], date, travelClass);
    }

    private TrainSearchResult buildResult(Train train, TrainStop from, TrainStop to,
                                          LocalDate journeyDate, TravelClass classFilter) {
        LocalDate runDate = journeyDate.minusDays(from.getDayOffset());
        int distance = to.getDistanceKm() - from.getDistanceKm();

        List<ClassAvailability> classes = new ArrayList<>();
        for (TrainClass c : train.getClasses()) {
            if (classFilter != null && c.getTravelClass() != classFilter) {
                continue;
            }
            int occupied = seatRepository.findOccupiedSeats(
                    train.getId(), runDate, c.getTravelClass(), from.getStopSequence(), to.getStopSequence()).size();
            classes.add(new ClassAvailability(c.getTravelClass(), c.getTotalSeats() - occupied,
                    c.getTotalSeats(), fare(c, distance)));
        }

        boolean intermediate = from.getStopSequence() != 1 || to.getStopSequence() != train.getStops().size();
        return new TrainSearchResult(
                train.getId(), train.getTrainNumber(), train.getName(),
                from.getStationCode(), from.getStationName(),
                to.getStationCode(), to.getStationName(),
                intermediate,
                journeyDate, from.getDepartureTime(),
                runDate.plusDays(to.getDayOffset()), to.getArrivalTime(),
                distance, classes);
    }


    // Internal: seat allocation (called by reservation-service)


    @Transactional
    public AllocationResponse allocate(AllocationRequest request) {
        if (request.passengerCount() < 1) {
            throw ApiException.badRequest("At least one passenger is required");
        }
        // Lock the class row FIRST, then read the train: an admin update/delete of this train holds the
        // same lock, so we always see the route as it is after that change (or 404 if it was deleted).
        Optional<TrainClass> lockedClass = trainClassRepository.findForUpdate(request.trainId(), request.travelClass());

        Train train = findTrain(request.trainId());
        TrainStop[] journey = resolveJourney(train, code(request.boardingStation()), code(request.destinationStation()));
        TrainStop from = journey[0];
        TrainStop to = journey[1];
        requireRunning(train, from, request.journeyDate());

        LocalDate runDate = request.journeyDate().minusDays(from.getDayOffset());
        LocalDateTime departure = LocalDateTime.of(request.journeyDate(), from.getDepartureTime());
        if (departure.isBefore(RailwayTime.now())) {
            throw ApiException.badRequest("Train " + train.getTrainNumber() + " has already departed from " + from.getStationName());
        }

        // The class row is locked (see above): until commit nobody else can allocate seats of this train/class.
        TrainClass trainClass = lockedClass
                .orElseThrow(() -> ApiException.badRequest(
                        "Train " + train.getTrainNumber() + " has no " + request.travelClass() + " class"));

        Set<Integer> occupied = new HashSet<>(seatRepository.findOccupiedSeats(
                train.getId(), runDate, request.travelClass(), from.getStopSequence(), to.getStopSequence()));
        int free = trainClass.getTotalSeats() - occupied.size();
        if (free < request.passengerCount()) {
            throw ApiException.conflict(free == 0
                    ? "No " + request.travelClass() + " seats available from " + from.getStationName() + " to " + to.getStationName()
                    : "Only " + free + " " + request.travelClass() + " seat(s) available from "
                    + from.getStationName() + " to " + to.getStationName());
        }

        // Pick the lowest-numbered free seats.
        List<Integer> seats = new ArrayList<>();
        for (int seat = 1; seat <= trainClass.getTotalSeats() && seats.size() < request.passengerCount(); seat++) {
            if (!occupied.contains(seat)) {
                seats.add(seat);
            }
        }
        List<SeatAllocation> rows = new ArrayList<>();
        for (int seat : seats) {
            rows.add(new SeatAllocation(train.getId(), runDate, request.travelClass(), seat,
                    from.getStopSequence(), to.getStopSequence(), request.holdRef()));
        }
        seatRepository.saveAll(rows);

        int distance = to.getDistanceKm() - from.getDistanceKm();
        BigDecimal perPassenger = fare(trainClass, distance);
        BigDecimal total = perPassenger.multiply(BigDecimal.valueOf(request.passengerCount()));
        log.info("Held seats {} on train {} ({} -> {}) for hold {}", seats, train.getTrainNumber(),
                from.getStationCode(), to.getStationCode(), request.holdRef());

        return new AllocationResponse(
                train.getId(), train.getTrainNumber(), train.getName(),
                from.getStationCode(), from.getStationName(),
                to.getStationCode(), to.getStationName(),
                runDate, request.journeyDate(), from.getDepartureTime(),
                runDate.plusDays(to.getDayOffset()), to.getArrivalTime(),
                distance, seats, perPassenger, total);
    }

    /** Frees every seat held by this reservation. Safe to call more than once. */
    @Transactional
    public void release(String holdRef) {
        int removed = seatRepository.deleteByHoldRef(holdRef);
        log.info("Released {} seat(s) for hold {}", removed, holdRef);
    }

    /**
     * Frees only the given seats of a booking (partial cancellation). Their segments become available
     * to other passengers again. Safe to call more than once.
     */
    @Transactional
    public void releaseSeats(ReleaseSeatsRequest request) {
        if (request.seatNumbers() == null || request.seatNumbers().isEmpty()) {
            return;
        }
        int removed = seatRepository.deleteByHoldRefAndSeatNumbers(request.holdRef(), request.seatNumbers());
        log.info("Released {} seat(s) {} for hold {}", removed, request.seatNumbers(), request.holdRef());
    }


    // helpers


    private Train findTrain(Long id) {
        return trainRepository.findById(id).orElseThrow(() -> ApiException.notFound("Train not found with id " + id));
    }

    /** Finds the boarding and destination stops on this train and checks their order. */
    private TrainStop[] resolveJourney(Train train, String sourceCode, String destinationCode) {
        TrainStop from = findStop(train, sourceCode);
        TrainStop to = findStop(train, destinationCode);
        if (from == null) {
            throw ApiException.badRequest("Station " + sourceCode + " is not on the route of train " + train.getTrainNumber());
        }
        if (to == null) {
            throw ApiException.badRequest("Station " + destinationCode + " is not on the route of train " + train.getTrainNumber());
        }
        if (from.getStopSequence() >= to.getStopSequence()) {
            throw ApiException.badRequest("On train " + train.getTrainNumber() + " " + to.getStationName()
                    + " comes before " + from.getStationName() + ". Destination must come after the boarding station.");
        }
        return new TrainStop[]{from, to};
    }

    private TrainStop findStop(Train train, String code) {
        return train.getStops().stream()
                .filter(s -> s.getStationCode().equals(code))
                .findFirst()
                .orElse(null);
    }

    private BigDecimal fare(TrainClass trainClass, int distanceKm) {
        return trainClass.getFarePerKm()
                .multiply(BigDecimal.valueOf(distanceKm))
                .max(minimumFare)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void validateDate(LocalDate date) {
        if (date.isBefore(RailwayTime.today())) {
            throw ApiException.badRequest("Journey date cannot be in the past");
        }
    }

    private void validateStops(List<StopRequest> stops) {
        Set<String> codes = new HashSet<>();
        int lastDistance = -1;
        int lastDay = 0;
        for (int i = 0; i < stops.size(); i++) {
            StopRequest s = stops.get(i);
            String stationCode = code(s.stationCode());
            if (!codes.add(stationCode)) {
                throw ApiException.badRequest("Station " + stationCode + " appears twice on the route");
            }
            boolean first = i == 0;
            boolean last = i == stops.size() - 1;
            if (!first && s.arrivalTime() == null) {
                throw ApiException.badRequest("Stop " + stationCode + " needs an arrivalTime");
            }
            if (!last && s.departureTime() == null) {
                throw ApiException.badRequest("Stop " + stationCode + " needs a departureTime");
            }
            if (first && (s.distanceKm() != 0 || s.dayOffset() != 0)) {
                throw ApiException.badRequest("The first stop must have distanceKm 0 and dayOffset 0");
            }
            if (s.distanceKm() <= lastDistance) {
                throw ApiException.badRequest("distanceKm must increase along the route (check " + stationCode + ")");
            }
            if (s.dayOffset() < lastDay) {
                throw ApiException.badRequest("dayOffset cannot decrease along the route (check " + stationCode + ")");
            }
            lastDistance = s.distanceKm();
            lastDay = s.dayOffset();
        }
    }

    private void validateClasses(List<ClassRequest> classes) {
        Set<TravelClass> seen = new HashSet<>();
        for (ClassRequest c : classes) {
            if (!seen.add(c.travelClass())) {
                throw ApiException.badRequest("Class " + c.travelClass() + " is listed twice");
            }
        }
    }

    /** Replaces all stops of the train (and its source/destination) with the given route. */
    private void replaceStops(Train train, List<StopRequest> stops) {
        train.getStops().clear();
        for (int i = 0; i < stops.size(); i++) {
            StopRequest s = stops.get(i);
            TrainStop stop = new TrainStop();
            stop.setTrain(train);
            stop.setStopSequence(i + 1);
            stop.setStationCode(code(s.stationCode()));
            stop.setStationName(s.stationName().trim());
            stop.setArrivalTime(s.arrivalTime());
            stop.setDepartureTime(s.departureTime());
            stop.setDayOffset(s.dayOffset());
            stop.setDistanceKm(s.distanceKm());
            train.getStops().add(stop);
        }
        train.setSource(code(stops.get(0).stationCode()));
        train.setDestination(code(stops.get(stops.size() - 1).stationCode()));
    }

    /** Locks every class row of the train (same lock that seat allocation takes), in a fixed order. */
    private void lockClasses(Train train) {
        train.getClasses().stream()
                .map(TrainClass::getTravelClass)
                .sorted()
                .forEach(c -> trainClassRepository.findForUpdate(train.getId(), c));
    }

    /**
     * Bookings with a run date before this are finished (the train reached its last stop before today),
     * so they no longer block an update or delete.
     */
    private LocalDate activeSince(Train train) {
        int lastDayOffset = train.getStops().isEmpty()
                ? 0 : train.getStops().get(train.getStops().size() - 1).getDayOffset();
        return RailwayTime.today().minusDays(lastDayOffset);
    }

    /** Old trains without running-day rows are treated as running daily. */
    private boolean runsOn(Train train, LocalDate runDate) {
        Set<DayOfWeek> days = train.getRunningDays();
        return days.isEmpty() || days.contains(runDate.getDayOfWeek());
    }

    /**
     * Running days are about the day the train LEAVES ITS ORIGIN (its "run date"), the same date seats are
     * tracked by. For a passenger boarding at the origin that is the requested date; for a stop reached after
     * midnight it is the requested date minus the stop's dayOffset.
     */
    private void requireRunning(Train train, TrainStop boarding, LocalDate journeyDate) {
        LocalDate runDate = journeyDate.minusDays(boarding.getDayOffset());
        if (!runsOn(train, runDate)) {
            throw ApiException.badRequest("Train " + train.getTrainNumber() + " does not leave " + train.getSource()
                    + " on " + runDate.getDayOfWeek() + ". Running days: " + sortedDays(train));
        }
    }

    private List<DayOfWeek> sortedDays(Train train) {
        return train.getRunningDays().isEmpty()
                ? List.of(DayOfWeek.values())
                : train.getRunningDays().stream().sorted().toList();
    }

    private String code(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private TrainDetailsResponse toDetails(Train train) {
        List<TrainDetailsResponse.StopInfo> stops = train.getStops().stream()
                .map(s -> new TrainDetailsResponse.StopInfo(s.getStopSequence(), s.getStationCode(), s.getStationName(),
                        s.getArrivalTime(), s.getDepartureTime(), s.getDayOffset(), s.getDistanceKm()))
                .toList();
        List<TrainDetailsResponse.ClassInfo> classes = train.getClasses().stream()
                .map(c -> new TrainDetailsResponse.ClassInfo(c.getTravelClass(), c.getTotalSeats(), c.getFarePerKm()))
                .toList();
        return new TrainDetailsResponse(train.getId(), train.getTrainNumber(), train.getName(),
                train.getSource(), train.getDestination(), stops, classes, sortedDays(train));
    }
}
