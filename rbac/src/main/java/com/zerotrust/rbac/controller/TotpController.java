package com.zerotrust.rbac.controller;

import com.zerotrust.rbac.service.TotpService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/totp")
public class TotpController {

    private final TotpService totpService;

    public TotpController(TotpService totpService) {
        this.totpService = totpService;
    }

    @PostMapping("/enroll")
    @PreAuthorize("hasRole('AUDITOR')")
    public Map<String, String> enroll(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        String otpauthUri = totpService.enrollUser(username);
        return Map.of("otpauthUri", otpauthUri);
    }

    @PostMapping("/verify")
    @PreAuthorize("hasRole('AUDITOR')")
    public Map<String, Boolean> verify(@RequestBody Map<String, String> body,
                                       @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        String code = body.get("code");
        boolean valid = totpService.validateCode(username, code);
        return Map.of("valid", valid);
    }
}
