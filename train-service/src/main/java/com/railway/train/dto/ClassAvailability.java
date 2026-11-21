package com.railway.train.dto;

import com.railway.train.model.TravelClass;

import java.math.BigDecimal;

public record ClassAvailability(TravelClass travelClass, int availableSeats, int totalSeats, BigDecimal farePerPassenger) {
}
