package com.villagebooker.config;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Test-only endpoints (lives in src/test): one per role, to check SecurityConfig end to end.
@RestController
@RequestMapping("/api/probe")
class RoleProbeController {

    @GetMapping("/user")
    @PreAuthorize("hasRole('USER')")
    String user() {
        return "ok";
    }

    @GetMapping("/owner")
    @PreAuthorize("hasRole('OWNER')")
    String owner() {
        return "ok";
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    String admin() {
        return "ok";
    }
}
