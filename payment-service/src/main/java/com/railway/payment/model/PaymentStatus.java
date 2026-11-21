package com.railway.payment.model;

public enum PaymentStatus {
    INITIATED,  // waiting for the gateway result
    SUCCESS,
    FAILED,
    PARTIALLY_REFUNDED,  // some passengers of the ticket were cancelled and refunded
    REFUNDED             // the whole ticket was cancelled and refunded
}
