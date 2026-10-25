package com.villagebooker.security;

import com.villagebooker.enums.Role;

// The logged-in user, built from the JWT (no database hit per request).
// In controllers: @AuthenticationPrincipal AuthUser user
public record AuthUser(Long id, String email, Role role) {
}
