package com.zerotrust.rbac.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    @GetMapping
    @PreAuthorize("hasAnyRole('READER', 'MANAGER')")
    public String listDocuments() {
        return "List of Confidencial Documents (Access: Reader e Manager)";
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public String createDocument(@AuthenticationPrincipal Jwt jwt) {
        Boolean isDeviceCompliant = jwt.getClaimAsBoolean("device_compliant");

        if (!Boolean.TRUE.equals(isDeviceCompliant)) {
            throw new AccessDeniedException("Blocked by zero trust. Device not valid by the IdP");
        }

        return "Document created with success (Access: only Manager)";
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public String removeDocument(
      @PathVariable String id,
      @AuthenticationPrincipal Jwt jwt
    ) {
        Boolean isDeviceCompliant = jwt.getClaimAsBoolean("device_compliant");

        if (!Boolean.TRUE.equals(isDeviceCompliant)) {
            throw new AccessDeniedException("Blocked by zero trust. Device not valid by the IdP");
        }

        return "Document " + id + " excluded with success (Access: only Manager)";
    }
}