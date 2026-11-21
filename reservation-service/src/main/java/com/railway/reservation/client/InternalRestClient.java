package com.railway.reservation.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railway.reservation.error.ApiException;
import com.railway.reservation.security.GatewayTrustFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;

/**
 * Builds the RestClient one service uses to call another one.
 * It adds the internal key header, sets timeouts, and converts error responses of the other
 * service back into an ApiException so the original message reaches the end user.
 */
public final class InternalRestClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private InternalRestClient() {
    }

    public static RestClient create(RestClient.Builder builder, String baseUrl, String apiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        return builder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(GatewayTrustFilter.KEY_HEADER, apiKey)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw toApiException(response);
                })
                .build();
    }

    private static ApiException toApiException(ClientHttpResponse response) throws IOException {
        int code = response.getStatusCode().value();
        String message = "Downstream service error";
        try {
            JsonNode node = MAPPER.readTree(response.getBody());
            message = node.path("message").asText(message);
        } catch (Exception ignored) {
            // body was not JSON - keep the default message
        }
        HttpStatus status = code >= 500 ? HttpStatus.BAD_GATEWAY : HttpStatus.resolve(code);
        return new ApiException(status == null ? HttpStatus.BAD_GATEWAY : status, message);
    }
}
