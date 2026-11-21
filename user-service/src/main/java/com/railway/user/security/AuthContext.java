package com.railway.user.security;

import com.railway.user.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads the user details the API gateway put on the request after validating the JWT,
 * or from Spring Security's SecurityContextHolder.
 * Preserves backward compatibility for controllers reading user identity.
 */
public final class AuthContext {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String EMAIL_HEADER = "X-User-Email";
    public static final String ROLE_HEADER = "X-User-Role";

    private AuthContext() {
    }

    public static Long userId(HttpServletRequest request) {
        String value = request.getHeader(USER_ID_HEADER);
        if (value == null || value.isBlank()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Long id) {
                return id;
            } else if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String str && !"anonymousUser".equals(str)) {
                try {
                    return Long.valueOf(str);
                } catch (NumberFormatException ignored) {
                }
            }
            throw ApiException.unauthorized("Missing user identity");
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            throw ApiException.unauthorized("Invalid user identity");
        }
    }

    public static String email(HttpServletRequest request) {
        return request.getHeader(EMAIL_HEADER);
    }

    public static boolean isAdmin(HttpServletRequest request) {
        String roleHeader = request.getHeader(ROLE_HEADER);
        if (roleHeader != null) {
            return "ADMIN".equals(roleHeader) || "ROLE_ADMIN".equals(roleHeader);
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            for (GrantedAuthority authority : auth.getAuthorities()) {
                if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                    return true;
                }
            }
        }
        return false;
    }
}
