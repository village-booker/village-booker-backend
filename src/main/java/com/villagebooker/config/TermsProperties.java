package com.villagebooker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// current versions of the terms of use; the accepted version is stored on the user as proof
@ConfigurationProperties(prefix = "app.terms")
public record TermsProperties(String version, String hostVersion) {
}
