package com.villagebooker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// link = the frontend page that takes the token: link + token is sent by email
@ConfigurationProperties(prefix = "app.password-reset")
public record PasswordResetProperties(Duration timeout, Duration minInterval, String link) {
}
