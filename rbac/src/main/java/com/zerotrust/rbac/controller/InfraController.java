package com.zerotrust.rbac.controller;

import com.zerotrust.rbac.model.PendingOperation;
import com.zerotrust.rbac.service.PendingOperationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class InfraController {

    private static final List<String> SERVERS = List.of("srv-web-01", "srv-app-02", "srv-db-03");
    private static final List<String> DATABASES = List.of("db-producao", "db-staging", "db-analytics");

    private final PendingOperationService pendingOperationService;

    public InfraController(PendingOperationService pendingOperationService) {
        this.pendingOperationService = pendingOperationService;
    }

    @GetMapping("/servers")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN', 'AUDITOR')")
    public List<String> listServers(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        System.out.println("[INFRA] User " + username + " listed servers");
        return SERVERS;
    }

    @GetMapping("/databases")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN', 'AUDITOR')")
    public List<String> listDatabases(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        System.out.println("[INFRA] User " + username + " listed databases");
        return DATABASES;
    }

    @PostMapping("/databases")
    @PreAuthorize("hasRole('ADMIN')")
    public String provisionDatabase(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        System.out.println("[INFRA] User " + username + " provisioned a new database");
        return "Database provisioned successfully by " + username;
    }

    @DeleteMapping("/databases/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PendingOperation> deleteDatabase(@PathVariable String id,
                                                           @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        PendingOperation op = pendingOperationService.create(username, "DELETE_DATABASE", id);
        return ResponseEntity.accepted().body(op);
    }

    @PutMapping("/databases/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PendingOperation> resetDatabases(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        PendingOperation op = pendingOperationService.create(username, "RESET_DATABASES", "all");
        return ResponseEntity.accepted().body(op);
    }
}
