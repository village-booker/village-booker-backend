package com.villagebooker.security;

import com.villagebooker.config.JwtProperties;
import com.villagebooker.enums.user.Role;
import com.villagebooker.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    // a separate key, so a verification link can never be used as a login token (and the other way round)
    private final SecretKey verificationKey;
    private final Duration expiration;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        byte[] secret = Decoders.BASE64.decode(properties.secret());
        this.key = Keys.hmacShaKeyFor(secret);
        this.verificationKey = Keys.hmacShaKeyFor(derive(secret, "email-verification"));
        this.expiration = properties.expiration();
        this.clock = clock;
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(expiration);
        String token = Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                // algorithm is pinned: without it jjwt picks HS256/384/512 by key length
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    // empty if the token is malformed, tampered with, signed with another key or expired
    public Optional<AuthUser> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(Instant.now(clock)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new AuthUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class))));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    // the email link (EmailVerificationService): user id and email, signed with verificationKey
    public IssuedToken issueEmailVerification(User user, Duration validFor) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(validFor);
        String token = Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(verificationKey, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    public Optional<VerifiedEmail> parseEmailVerification(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(verificationKey)
                    .clock(() -> Date.from(Instant.now(clock)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new VerifiedEmail(Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class)));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private static byte[] derive(byte[] secret, String purpose) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(purpose.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    public record VerifiedEmail(Long userId, String email) {
    }
}
