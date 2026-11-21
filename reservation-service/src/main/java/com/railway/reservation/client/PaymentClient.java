package com.railway.reservation.client;

import com.railway.reservation.client.InternalRestClient;
import com.railway.reservation.dto.RefundRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Calls payment-service (only used for refunds on cancellation). */
@Component
public class PaymentClient {

    private final RestClient restClient;

    public PaymentClient(RestClient.Builder builder,
                         @Value("${services.payment-url}") String baseUrl,
                         @Value("${internal.api-key}") String apiKey) {
        this.restClient = InternalRestClient.create(builder, baseUrl, apiKey);
    }

    public void refund(RefundRequest request) {
        restClient.post().uri("/internal/payments/refund").body(request).retrieve().toBodilessEntity();
    }
}
