package com.zerotrust.rbac.controller;

import com.zerotrust.rbac.model.PendingOperation;
import com.zerotrust.rbac.service.PendingOperationService;
import com.zerotrust.rbac.service.TotpService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/operations")
public class OperationController {

    private final PendingOperationService pendingOperationService;
    private final TotpService totpService;

    public OperationController(PendingOperationService pendingOperationService, TotpService totpService) {
        this.pendingOperationService = pendingOperationService;
        this.totpService = totpService;
    }

    @GetMapping
    @PreAuthorize("hasRole('AUDITOR')")
    public List<PendingOperation> listPending() {
        return pendingOperationService.listPending();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public PendingOperation getById(@PathVariable String id) {
        return pendingOperationService.getById(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('AUDITOR')")
    public PendingOperation approve(@PathVariable String id,
                                    @RequestBody(required = false) Map<String, String> body,
                                    @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");

        if (!totpService.isEnrolled(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "TOTP not enrolled. Please enroll at /api/totp/enroll first.");
        }

        String totpCode = body != null ? body.get("totpCode") : null;
        if (totpCode == null || !totpService.validateCode(username, totpCode)) {
            System.out.println("[AUDIT] TOTP_FAILED | user=" + username + " | operationId=" + id);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid TOTP code");
        }

        System.out.println("[AUDIT] TOTP_VALIDATED | user=" + username + " | operationId=" + id);
        return pendingOperationService.approve(id, username);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('AUDITOR')")
    public PendingOperation reject(@PathVariable String id,
                                   @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        return pendingOperationService.reject(id, username);
    }
}
