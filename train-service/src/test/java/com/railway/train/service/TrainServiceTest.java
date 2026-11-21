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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Core business-logic unit tests for TrainService.
 *
 * Designed as a clean reference for learning and explaining railway system concepts:
 * 1. Train Creation & Route Validation (stops order, unique stations)
 * 2. Intermediate Station Search & Fare Calculation
 * 3. Seat Allocation (concurrency locking, picking lowest available seats)
 * 4. Safe Train Deletion (booking protection rule)
 * 5. Releasing Seats (full and partial cancellations)
 */
@ExtendWith(MockitoExtension.class)
class TrainServiceTest {

    @Mock
    private TrainRepository trainRepository;

    @Mock
    private TrainClassRepository trainClassRepository;

    @Mock
    private SeatAllocationRepository seatRepository;

    // Minimum fare configuration (e.g. 50.00 INR minimum charge)
    private final BigDecimal minimumFare = new BigDecimal("50.00");

    private TrainService trainService;
    private MockedStatic<RailwayTime> mockedRailwayTime;

    // Fixed mock dates for deterministic, explainable tests (Tuesday, 10:00 AM)
    private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 11, 10);
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 11, 10, 10, 0);

    @BeforeEach
    void setUp() {
        trainService = new TrainService(trainRepository, trainClassRepository, seatRepository, minimumFare);
        mockedRailwayTime = Mockito.mockStatic(RailwayTime.class);
        mockedRailwayTime.when(RailwayTime::today).thenReturn(FIXED_TODAY);
        mockedRailwayTime.when(RailwayTime::now).thenReturn(FIXED_NOW);
    }

    @AfterEach
    void tearDown() {
        if (mockedRailwayTime != null) {
            mockedRailwayTime.close();
        }
    }

    /**
     * Creates a standard test train: Mumbai (MUM) -> Surat (ST) -> New Delhi (DEL).
     * MUM: departure 16:00 (Day 0, 0 km)
     * ST:  arrival 19:00, departure 19:10 (Day 0, 260 km)
     * DEL: arrival 08:00 (Day 1, 1380 km)
     * Classes: SL (72 seats @ 0.50/km), AC3 (64 seats @ 1.50/km)
     */
    private Train createSampleTrain(Long id, String trainNumber, Set<DayOfWeek> runningDays) {
        Train train = new Train();
        train.setId(id);
        train.setTrainNumber(trainNumber);
        train.setName("Superfast Express");
        train.setSource("MUM");
        train.setDestination("DEL");

        if (runningDays != null) {
            train.getRunningDays().addAll(runningDays);
        }

        TrainStop s1 = new TrainStop();
        s1.setId(101L);
        s1.setTrain(train);
        s1.setStopSequence(1);
        s1.setStationCode("MUM");
        s1.setStationName("Mumbai Central");
        s1.setDepartureTime(LocalTime.of(16, 0));
        s1.setDayOffset(0);
        s1.setDistanceKm(0);

        TrainStop s2 = new TrainStop();
        s2.setId(102L);
        s2.setTrain(train);
        s2.setStopSequence(2);
        s2.setStationCode("ST");
        s2.setStationName("Surat");
        s2.setArrivalTime(LocalTime.of(19, 0));
        s2.setDepartureTime(LocalTime.of(19, 10));
        s2.setDayOffset(0);
        s2.setDistanceKm(260);

        TrainStop s3 = new TrainStop();
        s3.setId(103L);
        s3.setTrain(train);
        s3.setStopSequence(3);
        s3.setStationCode("DEL");
        s3.setStationName("New Delhi");
        s3.setArrivalTime(LocalTime.of(8, 0));
        s3.setDayOffset(1);
        s3.setDistanceKm(1380);

        train.getStops().addAll(List.of(s1, s2, s3));

        TrainClass sl = new TrainClass();
        sl.setId(201L);
        sl.setTrain(train);
        sl.setTravelClass(TravelClass.SL);
        sl.setTotalSeats(72);
        sl.setFarePerKm(new BigDecimal("0.50"));

        TrainClass ac3 = new TrainClass();
        ac3.setId(202L);
        ac3.setTrain(train);
        ac3.setTravelClass(TravelClass.AC3);
        ac3.setTotalSeats(64);
        ac3.setFarePerKm(new BigDecimal("1.50"));

        train.getClasses().addAll(List.of(sl, ac3));

        return train;
    }

    // =========================================================================
    // 1. Train Creation & Validation
    // =========================================================================
    @Nested
    @DisplayName("1. Train Creation")
    class TrainCreationTests {

        @Test
        @DisplayName("Should successfully create a train and calculate stops, classes, and running days")
        void shouldCreateTrainSuccessfully() {
            // Arrange
            List<StopRequest> stops = List.of(
                    new StopRequest("MUM", "Mumbai Central", null, LocalTime.of(16, 0), 0, 0),
                    new StopRequest("ST", "Surat", LocalTime.of(19, 0), LocalTime.of(19, 10), 0, 260),
                    new StopRequest("DEL", "New Delhi", LocalTime.of(8, 0), null, 1, 1380)
            );
            List<ClassRequest> classes = List.of(
                    new ClassRequest(TravelClass.SL, 72, new BigDecimal("0.50")),
                    new ClassRequest(TravelClass.AC3, 64, new BigDecimal("1.50"))
            );
            CreateTrainRequest request = new CreateTrainRequest(
                    "12951", "Mumbai - Delhi Express", stops, classes,
                    List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
            );

            when(trainRepository.existsByTrainNumber("12951")).thenReturn(false);

            // Act
            TrainDetailsResponse response = trainService.createTrain(request);

            // Assert
            assertThat(response.trainNumber()).isEqualTo("12951");
            assertThat(response.source()).isEqualTo("MUM");
            assertThat(response.destination()).isEqualTo("DEL");
            assertThat(response.stops()).hasSize(3);
            assertThat(response.classes()).hasSize(2);
            assertThat(response.runningDays()).containsExactly(DayOfWeek.MONDAY, DayOfWeek.TUESDAY);

            verify(trainRepository).save(any(Train.class));
        }

        @Test
        @DisplayName("Should reject train creation when train number already exists (Conflict 409)")
        void shouldRejectDuplicateTrainNumber() {
            CreateTrainRequest request = new CreateTrainRequest("12951", "Express", List.of(), List.of(), null);
            when(trainRepository.existsByTrainNumber("12951")).thenReturn(true);

            assertThatThrownBy(() -> trainService.createTrain(request))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Train 12951 already exists")
                    .matches(e -> ((ApiException) e).getStatus() == HttpStatus.CONFLICT);

            verify(trainRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject invalid route where station distance does not increase monotonically (Bad Request 400)")
        void shouldRejectInvalidRouteDistance() {
            List<StopRequest> stops = List.of(
                    new StopRequest("MUM", "Mumbai Central", null, LocalTime.of(16, 0), 0, 0),
                    new StopRequest("ST", "Surat", LocalTime.of(19, 0), LocalTime.of(19, 10), 0, 260),
                    new StopRequest("DEL", "New Delhi", LocalTime.of(8, 0), null, 1, 260) // invalid: 260 <= 260
            );
            CreateTrainRequest request = new CreateTrainRequest("12951", "Express", stops, List.of(), null);
            when(trainRepository.existsByTrainNumber("12951")).thenReturn(false);

            assertThatThrownBy(() -> trainService.createTrain(request))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("distanceKm must increase along the route (check DEL)")
                    .matches(e -> ((ApiException) e).getStatus() == HttpStatus.BAD_REQUEST);
        }
    }

    // =========================================================================
    // 2. Search & Availability for Intermediate Stations
    // =========================================================================
    @Nested
    @DisplayName("2. Search & Availability")
    class SearchTests {

        @Test
        @DisplayName("Should find train and compute available seats and fare for intermediate journey (MUM -> ST)")
        void shouldFindIntermediateJourneyWithAccurateSeatsAndFare() {
            // Arrange
            Train train = createSampleTrain(1L, "12951", Set.of(DayOfWeek.TUESDAY));
            when(trainRepository.findTrainsBetween("MUM", "ST")).thenReturn(List.of(train));

            // Suppose 3 seats are booked on this segment for Sleeper class
            when(seatRepository.findOccupiedSeats(1L, FIXED_TODAY, TravelClass.SL, 1, 2))
                    .thenReturn(List.of(1, 2, 3));
            when(seatRepository.findOccupiedSeats(1L, FIXED_TODAY, TravelClass.AC3, 1, 2))
                    .thenReturn(List.of());

            // Act
            List<TrainSearchResult> results = trainService.search("MUM", "ST", FIXED_TODAY, null);

            // Assert
            assertThat(results).hasSize(1);
            TrainSearchResult result = results.get(0);
            assertThat(result.intermediateBooking()).isTrue();
            assertThat(result.distanceKm()).isEqualTo(260);

            // SL fare: 260 km * 0.50/km = 130.00; available seats = 72 - 3 = 69
            ClassAvailability sl = result.classes().stream()
                    .filter(c -> c.travelClass() == TravelClass.SL).findFirst().orElseThrow();
            assertThat(sl.availableSeats()).isEqualTo(69);
            assertThat(sl.totalSeats()).isEqualTo(72);
            assertThat(sl.farePerPassenger()).isEqualByComparingTo("130.00");
        }

        @Test
        @DisplayName("Should apply configured minimum fare when distance * rate is below threshold")
        void shouldEnforceMinimumFare() {
            Train train = createSampleTrain(1L, "12951", Set.of(DayOfWeek.TUESDAY));
            train.getStops().get(1).setDistanceKm(10); // 10 km * 0.50 = 5.00 < 50.00 minimum fare

            when(trainRepository.findTrainsBetween("MUM", "ST")).thenReturn(List.of(train));
            when(seatRepository.findOccupiedSeats(1L, FIXED_TODAY, TravelClass.SL, 1, 2)).thenReturn(List.of());

            List<TrainSearchResult> results = trainService.search("MUM", "ST", FIXED_TODAY, TravelClass.SL);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).classes().get(0).farePerPassenger()).isEqualByComparingTo("50.00");
        }

        @Test
        @DisplayName("Should skip trains that have already departed before current time")
        void shouldSkipTrainIfAlreadyDeparted() {
            Train train = createSampleTrain(1L, "12951", Set.of(DayOfWeek.TUESDAY));
            // Train departed at 09:00 AM, but current mocked time is 10:00 AM
            train.getStops().get(0).setDepartureTime(LocalTime.of(9, 0));

            when(trainRepository.findTrainsBetween("MUM", "DEL")).thenReturn(List.of(train));

            List<TrainSearchResult> results = trainService.search("MUM", "DEL", FIXED_TODAY, null);

            assertThat(results).isEmpty();
        }
    }

    // =========================================================================
    // 3. Seat Allocation (Booking Engine)
    // =========================================================================
    @Nested
    @DisplayName("3. Seat Allocation")
    class SeatAllocationTests {

        @Test
        @DisplayName("Should lock class row, pick lowest available seat numbers, and persist seat allocations")
        void shouldAllocateLowestFreeSeatsSuccessfully() {
            // Arrange
            Train train = createSampleTrain(1L, "12951", Set.of(DayOfWeek.TUESDAY));
            TrainClass slClass = train.getClasses().get(0); // SL class (72 seats)

            AllocationRequest request = new AllocationRequest(
                    "HOLD-123", 1L, FIXED_TODAY, TravelClass.SL, "MUM", "ST", 2
            );

            // Class row is locked first with findForUpdate to avoid race conditions
            when(trainClassRepository.findForUpdate(1L, TravelClass.SL)).thenReturn(Optional.of(slClass));
            when(trainRepository.findById(1L)).thenReturn(Optional.of(train));

            // Seat 1 is already taken; system should allocate lowest available: [2, 3]
            when(seatRepository.findOccupiedSeats(1L, FIXED_TODAY, TravelClass.SL, 1, 2))
                    .thenReturn(List.of(1));

            // Act
            AllocationResponse response = trainService.allocate(request);

            // Assert
            assertThat(response.seatNumbers()).containsExactly(2, 3);
            assertThat(response.farePerPassenger()).isEqualByComparingTo("130.00");
            assertThat(response.totalFare()).isEqualByComparingTo("260.00");

            ArgumentCaptor<List<SeatAllocation>> captor = ArgumentCaptor.forClass(List.class);
            verify(seatRepository).saveAll(captor.capture());
            List<SeatAllocation> savedSeats = captor.getValue();
            assertThat(savedSeats).hasSize(2);
            assertThat(savedSeats.get(0).getSeatNumber()).isEqualTo(2);
            assertThat(savedSeats.get(0).getHoldRef()).isEqualTo("HOLD-123");
            assertThat(savedSeats.get(0).getFromSeq()).isEqualTo(1);
            assertThat(savedSeats.get(0).getToSeq()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should reject allocation with Conflict (409) when seats are sold out")
        void shouldRejectAllocationWhenSoldOut() {
            Train train = createSampleTrain(1L, "12951", Set.of(DayOfWeek.TUESDAY));
            TrainClass slClass = train.getClasses().get(0);
            slClass.setTotalSeats(2); // Only 2 total seats

            AllocationRequest request = new AllocationRequest(
                    "HOLD-123", 1L, FIXED_TODAY, TravelClass.SL, "MUM", "ST", 1
            );

            when(trainClassRepository.findForUpdate(1L, TravelClass.SL)).thenReturn(Optional.of(slClass));
            when(trainRepository.findById(1L)).thenReturn(Optional.of(train));
            when(seatRepository.findOccupiedSeats(1L, FIXED_TODAY, TravelClass.SL, 1, 2))
                    .thenReturn(List.of(1, 2)); // Both occupied

            assertThatThrownBy(() -> trainService.allocate(request))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("No SL seats available from Mumbai Central to Surat")
                    .matches(e -> ((ApiException) e).getStatus() == HttpStatus.CONFLICT);

            verify(seatRepository, never()).saveAll(any());
        }
    }

    // =========================================================================
    // 4. Safe Train Deletion (Integrity Rule)
    // =========================================================================
    @Nested
    @DisplayName("4. Train Deletion")
    class TrainDeletionTests {

        @Test
        @DisplayName("Should successfully delete train when it has no upcoming active bookings")
        void shouldDeleteTrainWhenNoBookingsExist() {
            Train train = createSampleTrain(1L, "12951", Set.of(DayOfWeek.MONDAY));
            when(trainRepository.findById(1L)).thenReturn(Optional.of(train));

            // Origin offset 0, terminus offset 1 -> active threshold is FIXED_TODAY - 1 day
            LocalDate activeSince = FIXED_TODAY.minusDays(1);
            when(seatRepository.countActiveBookings(1L, activeSince)).thenReturn(0L);

            // Act
            trainService.deleteTrain(1L);

            // Assert
            verify(seatRepository).deleteByTrainId(1L);
            verify(trainRepository).delete(train);
        }

        @Test
        @DisplayName("Should block train deletion with Conflict (409) when passengers have upcoming bookings")
        void shouldBlockDeletionWhenUpcomingBookingsExist() {
            Train train = createSampleTrain(1L, "12951", Set.of(DayOfWeek.MONDAY));
            when(trainRepository.findById(1L)).thenReturn(Optional.of(train));

            LocalDate activeSince = FIXED_TODAY.minusDays(1);
            when(seatRepository.countActiveBookings(1L, activeSince)).thenReturn(3L); // 3 active bookings

            assertThatThrownBy(() -> trainService.deleteTrain(1L))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Train 12951 cannot be deleted: it has 3 upcoming booking(s)")
                    .matches(e -> ((ApiException) e).getStatus() == HttpStatus.CONFLICT);

            verify(seatRepository, never()).deleteByTrainId(anyLong());
            verify(trainRepository, never()).delete(any());
        }
    }

    // =========================================================================
    // 5. Seat Release (Cancellation Flow)
    // =========================================================================
    @Nested
    @DisplayName("5. Seat Release / Cancellation")
    class SeatReleaseTests {

        @Test
        @DisplayName("Should release all held seats when full ticket is cancelled")
        void shouldReleaseAllSeatsForHold() {
            when(seatRepository.deleteByHoldRef("HOLD-123")).thenReturn(2);

            trainService.release("HOLD-123");

            verify(seatRepository).deleteByHoldRef("HOLD-123");
        }

        @Test
        @DisplayName("Should release only specified seats during partial passenger cancellation")
        void shouldReleaseSpecificSeatsForPartialCancellation() {
            ReleaseSeatsRequest request = new ReleaseSeatsRequest("HOLD-123", List.of(2));
            when(seatRepository.deleteByHoldRefAndSeatNumbers("HOLD-123", List.of(2))).thenReturn(1);

            trainService.releaseSeats(request);

            verify(seatRepository).deleteByHoldRefAndSeatNumbers("HOLD-123", List.of(2));
        }
    }
}
