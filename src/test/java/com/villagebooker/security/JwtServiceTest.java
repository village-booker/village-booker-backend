package com.villagebooker.security;

import com.villagebooker.config.JwtProperties;
import com.villagebooker.enums.Role;
import com.villagebooker.model.User;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[64]);
    private static final String OTHER_SECRET = Base64.getEncoder().encodeToString("x".repeat(64).getBytes());
    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, Duration.ofHours(24)), clock);

    @Test
    void issuedTokenParsesBackToTheSameUser() {
        JwtService.IssuedToken token = jwtService.issue(user(42L, "ana@example.com", Role.OWNER));

        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(jwtService.parse(token.token()))
                .contains(new AuthUser(42L, "ana@example.com", Role.OWNER));
    }

    @Test
    void expiredTokenIsRejected() {
        String token = jwtService.issue(user(1L, "a@example.com", Role.USER)).token();

        Clock dayLater = Clock.fixed(NOW.plus(Duration.ofHours(25)), ZoneOffset.UTC);
        JwtService later = new JwtService(new JwtProperties(SECRET, Duration.ofHours(24)), dayLater);

        assertThat(later.parse(token)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtService attacker = new JwtService(new JwtProperties(OTHER_SECRET, Duration.ofHours(24)), clock);
        String forged = attacker.issue(user(1L, "a@example.com", Role.ADMIN)).token();

        assertThat(jwtService.parse(forged)).isEmpty();
    }

    @Test
    void tamperedPayloadIsRejected() {
        String token = jwtService.issue(user(1L, "a@example.com", Role.USER)).token();
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        String adminPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.replace("\"USER\"", "\"ADMIN\"").getBytes());

        assertThat(jwtService.parse(parts[0] + "." + adminPayload + "." + parts[2])).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(jwtService.parse("not-a-token")).isEmpty();
        assertThat(jwtService.parse("")).isEmpty();
    }

    private static User user(Long id, String email, Role role) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        return user;
    }
}
