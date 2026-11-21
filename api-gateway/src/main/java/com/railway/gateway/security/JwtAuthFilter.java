package com.railway.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * The ONE place where requests are checked. For every request that goes through the gateway:
 *
 *  1. rejects suspicious paths (".." or anything under /internal)
 *  2. if the path is not public: requires a valid JWT (401 otherwise)
 *  3. /api/admin/** additionally requires role ADMIN (403 otherwise)
 *  4. removes the Authorization header and any X-User-* / X-Internal-Key headers the client sent
 *  5. adds the trusted headers: X-Internal-Key (proves "I came from the gateway") and,
 *     for logged-in users, X-User-Id / X-User-Email / X-User-Role
 *
 * Downstream services never see the token and never validate it - they trust these headers.
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    public static final String KEY_HEADER = "X-Internal-Key";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String EMAIL_HEADER = "X-User-Email";
    public static final String ROLE_HEADER = "X-User-Role";

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final SecretKey key;
    private final ObjectMapper objectMapper;
    private final String internalKey;
    private final List<String> publicPaths;

    public JwtAuthFilter(@Value("${jwt.secret}") String secret,
                         @Value("${internal.api-key}") String internalKey,
                         @Value("${gateway.public-paths}") String publicPaths,
                         ObjectMapper objectMapper) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.internalKey = internalKey;
        this.objectMapper = objectMapper;
        this.publicPaths = Arrays.stream(publicPaths.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath(); // already URL-decoded

        // Internal endpoints are never reachable from outside.
        if (path.contains("..") || path.startsWith("/internal") || path.contains("/internal/")) {
            return reject(exchange, HttpStatus.NOT_FOUND, "Resource not found");
        }

        Claims claims = null;
        if (!isPublic(path)) {
            String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (header == null || !header.startsWith("Bearer ")) {
                return reject(exchange, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
            }
            try {
                claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(header.substring(7)).getPayload();
            } catch (JwtException | IllegalArgumentException ex) {
                return reject(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
            }
            if (path.startsWith("/api/admin/") && !"ADMIN".equals(claims.get("role", String.class))) {
                return reject(exchange, HttpStatus.FORBIDDEN, "Admin access required");
            }
        }

        final Claims user = claims;
        ServerHttpRequest trusted = request.mutate().headers(headers -> {
            headers.remove(HttpHeaders.AUTHORIZATION);   // services never see the token
            headers.remove(USER_ID_HEADER);              // never trust what the client sends
            headers.remove(EMAIL_HEADER);
            headers.remove(ROLE_HEADER);
            headers.set(KEY_HEADER, internalKey);
            if (user != null) {
                headers.set(USER_ID_HEADER, user.getSubject());
                String email = user.get("email", String.class);
                String role = user.get("role", String.class);
                if (email != null) {
                    headers.set(EMAIL_HEADER, email);
                }
                if (role != null) {
                    headers.set(ROLE_HEADER, role);
                }
            }
        }).build();

        return chain.filter(exchange.mutate().request(trusted).build());
    }

    private boolean isPublic(String path) {
        for (String pattern : publicPaths) {
            if (matcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        ErrorBody body = new ErrorBody(Instant.now().toString(), status.value(), status.getReasonPhrase(),
                message, exchange.getRequest().getURI().getPath());
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException ex) {
            bytes = "{}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    /** Same JSON shape as the error responses of the services. */
    private record ErrorBody(String timestamp, int status, String error, String message, String path) {
    }

    @Override
    public int getOrder() {
        return -1; // before the routing filters
    }
}
