package com.zerotrust.rbac.config;

import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

public class DPopValidationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            
            // Extract cnf insert by Keycloak
            Map<String, Object> cnf = jwt.getClaim("cnf");
            
            // if the token be DPoP (has cnf), needs prof
            if (cnf != null && cnf.containsKey("jkt")) {
                String tokenJkt = (String) cnf.get("jkt");
                String dpopHeader = request.getHeader("DPoP");

                if (dpopHeader == null || dpopHeader.isEmpty()) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Zero Trust: Header DPoP absent.");
                    return;
                }

                try {
                    // reads public key (JWK) sent in the header by the client (postman)
                    SignedJWT dpopProof = SignedJWT.parse(dpopHeader);
                    
                    // does SHA-256 of the public key (Thumbprint) using Nimbus
                    String clientJkt = dpopProof.getHeader().getJWK().computeThumbprint().toString();

                    // compare the digital from the client with bounded in the token
                    if (!tokenJkt.equals(clientJkt)) {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Zero Trust: Violation of DPoP. Possible leaked token.");
                        return;
                    }
                } catch (Exception e) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Zero Trust: Proof DPoP invalid.");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}