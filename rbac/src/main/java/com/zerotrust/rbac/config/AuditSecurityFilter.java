package com.zerotrust.rbac.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class AuditSecurityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuditSecurityFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Verify if the request is with valid JWT
        if (authentication != null && authentication.isAuthenticated() && !authentication.getName().equals("anonymousUser")) {
            log.info("[AUDIT] User: '{}' | Roles: {} | Access attempt: {} {}",
                    authentication.getName(),
                    authentication.getAuthorities(),
                    request.getMethod(),
                    request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }
}