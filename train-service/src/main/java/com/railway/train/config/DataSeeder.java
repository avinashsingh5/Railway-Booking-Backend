package com.railway.train.config;

import com.railway.train.model.TravelClass;
import com.railway.train.dto.CreateTrainRequest;
import com.railway.train.dto.CreateTrainRequest.ClassRequest;
import com.railway.train.dto.CreateTrainRequest.StopRequest;
import com.railway.train.repository.TrainRepository;
import com.railway.train.service.TrainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * Sample trains so you can test straight away (12009 runs Monday-Friday only, the others daily). Seat counts are deliberately tiny
 * (SL = 6) so you can quickly see "sold out on one segment, free on another".
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final List<DayOfWeek> DAILY = List.of(DayOfWeek.values());
    private static final List<DayOfWeek> WEEKDAYS = List.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final TrainRepository trainRepository;
    private final TrainService trainService;

    public DataSeeder(TrainRepository trainRepository, TrainService trainService) {
        this.trainRepository = trainRepository;
        this.trainService = trainService;
    }

    @Override
    public void run(String... args) {
        if (trainRepository.count() > 0) {
            return;
        }

        trainService.createTrain(new CreateTrainRequest("12951", "Mumbai - Delhi Express", List.of(
                stop("MUM", "Mumbai Central", null, "17:00", 0, 0),
                stop("ST", "Surat", "19:45", "19:50", 0, 263),
                stop("BRC", "Vadodara", "21:15", "21:20", 0, 392),
                stop("RTM", "Ratlam", "00:05", "00:10", 1, 659),
                stop("KOTA", "Kota", "03:10", "03:15", 1, 920),
                stop("DEL", "New Delhi", "08:30", null, 1, 1384)), classes(), DAILY));

        trainService.createTrain(new CreateTrainRequest("12952", "Delhi - Mumbai Express", List.of(
                stop("DEL", "New Delhi", null, "16:55", 0, 0),
                stop("KOTA", "Kota", "22:15", "22:20", 0, 464),
                stop("RTM", "Ratlam", "01:15", "01:20", 1, 725),
                stop("BRC", "Vadodara", "05:05", "05:10", 1, 992),
                stop("ST", "Surat", "06:30", "06:35", 1, 1121),
                stop("MUM", "Mumbai Central", "09:15", null, 1, 1384)), classes(), DAILY));

        trainService.createTrain(new CreateTrainRequest("12009", "Mumbai - Ahmedabad Express", List.of(
                stop("MUM", "Mumbai Central", null, "06:25", 0, 0),
                stop("ST", "Surat", "09:10", "09:15", 0, 263),
                stop("BRC", "Vadodara", "10:45", "10:50", 0, 392),
                stop("ADI", "Ahmedabad", "12:30", null, 0, 491)), classes(), WEEKDAYS));

        log.info("Seeded {} sample trains", trainRepository.count());
    }

    private StopRequest stop(String code, String name, String arrival, String departure, int dayOffset, int km) {
        return new StopRequest(code, name,
                arrival == null ? null : LocalTime.parse(arrival),
                departure == null ? null : LocalTime.parse(departure),
                dayOffset, km);
    }

    private List<ClassRequest> classes() {
        return List.of(
                new ClassRequest(TravelClass.SL, 6, new BigDecimal("0.60")),
                new ClassRequest(TravelClass.AC3, 4, new BigDecimal("1.60")),
                new ClassRequest(TravelClass.AC2, 4, new BigDecimal("2.30")),
                new ClassRequest(TravelClass.AC1, 2, new BigDecimal("3.80")));
    }
}
