package com.villagebooker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// how long a booking may stay in a status that expires (spec 8): expiresAt = now + timeout
@ConfigurationProperties(prefix = "app.booking")
public record BookingProperties(Duration ownerApprovalTimeout, Duration paymentTimeout) {
}
