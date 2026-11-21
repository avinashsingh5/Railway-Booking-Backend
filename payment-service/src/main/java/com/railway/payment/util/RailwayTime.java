package com.railway.payment.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** All railway times are treated as Indian Standard Time. */
public final class RailwayTime {
    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private RailwayTime() {
    }

    public static LocalDate today() {
        return LocalDate.now(IST);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(IST);
    }
}
