package com.railway.payment.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railway.payment.error.ApiException;
import com.railway.payment.security.GatewayTrustFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;


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
