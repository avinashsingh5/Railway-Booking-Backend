package com.railway.reservation.client;

import com.railway.reservation.client.InternalRestClient;
import com.railway.reservation.dto.AllocationRequest;
import com.railway.reservation.dto.AllocationResponse;
import com.railway.reservation.dto.ReleaseSeatsRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Calls train-service (synchronous REST). */
@Component
public class TrainClient {

    private final RestClient restClient;

    public TrainClient(RestClient.Builder builder,
                       @Value("${services.train-url}") String baseUrl,
                       @Value("${internal.api-key}") String apiKey) {
        this.restClient = InternalRestClient.create(builder, baseUrl, apiKey);
    }

    public AllocationResponse allocate(AllocationRequest request) {
        return restClient.post().uri("/internal/trains/allocate").body(request).retrieve().body(AllocationResponse.class);
    }

    public void release(String holdRef) {
        restClient.post().uri("/internal/trains/release/{holdRef}", holdRef).retrieve().toBodilessEntity();
    }

    /** Frees only the given seats of a booking (partial cancellation). */
    public void releaseSeats(ReleaseSeatsRequest request) {
        restClient.post().uri("/internal/trains/release-seats").body(request).retrieve().toBodilessEntity();
    }
}
