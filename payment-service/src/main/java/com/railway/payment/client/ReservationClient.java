package com.railway.payment.client;

import com.railway.payment.client.InternalRestClient;
import com.railway.payment.dto.PaymentResultRequest;
import com.railway.payment.dto.ReservationSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Calls reservation-service. */
@Component
public class ReservationClient {

    private final RestClient restClient;

    public ReservationClient(RestClient.Builder builder,
                             @Value("${services.reservation-url}") String baseUrl,
                             @Value("${internal.api-key}") String apiKey) {
        this.restClient = InternalRestClient.create(builder, baseUrl, apiKey);
    }

    public ReservationSummary getSummary(Long reservationId) {
        return restClient.get().uri("/internal/reservations/{id}", reservationId).retrieve().body(ReservationSummary.class);
    }

    public void notifyPaymentResult(Long reservationId, PaymentResultRequest result) {
        restClient.post().uri("/internal/reservations/{id}/payment-result", reservationId)
                .body(result).retrieve().toBodilessEntity();
    }
}
