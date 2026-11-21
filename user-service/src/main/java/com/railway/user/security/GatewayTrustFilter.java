package com.railway.user.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railway.user.error.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;

/**
 * Validates that requests come from the API Gateway or another internal service via X-Internal-Key.
 * Also populates Spring Security's SecurityContextHolder with user credentials and roles
 * forwarded by the API Gateway in headers (X-User-Id, X-User-Role).
 */
@Component
public class GatewayTrustFilter extends OncePerRequestFilter {

    public static final String KEY_HEADER = "X-Internal-Key";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String ROLE_HEADER = "X-User-Role";

    private final ObjectMapper objectMapper;
    private final byte[] expectedKey;

    public GatewayTrustFilter(ObjectMapper objectMapper, @Value("${internal.api-key}") String internalKey) {
        this.objectMapper = objectMapper;
        this.expectedKey = internalKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();

        // Allow documentation / error endpoints to bypass internal key verification
        if (isPermittedWithoutKey(uri)) {
            chain.doFilter(request, response);
            return;
        }

        String supplied = request.getHeader(KEY_HEADER);
        boolean trusted = supplied != null
                && MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8), expectedKey);

        if (!trusted) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(HttpStatus.UNAUTHORIZED,
                    "Requests must go through the API gateway", uri));
            return;
        }

        // Populate SecurityContextHolder if user headers are present
        String userIdStr = request.getHeader(USER_ID_HEADER);
        if (userIdStr != null && !userIdStr.isBlank()) {
            String roleStr = request.getHeader(ROLE_HEADER);
            List<SimpleGrantedAuthority> authorities;
            if (roleStr != null && !roleStr.isBlank()) {
                String roleName = roleStr.startsWith("ROLE_") ? roleStr : "ROLE_" + roleStr;
                authorities = List.of(new SimpleGrantedAuthority(roleName));
            } else {
                authorities = Collections.emptyList();
            }

            Object principal;
            try {
                principal = Long.valueOf(userIdStr);
            } catch (NumberFormatException e) {
                principal = userIdStr;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isPermittedWithoutKey(String uri) {
        return uri.equals("/error")
                || uri.equals("/swagger-ui.html")
                || uri.startsWith("/swagger-ui/")
                || uri.startsWith("/v3/api-docs");
    }
}
