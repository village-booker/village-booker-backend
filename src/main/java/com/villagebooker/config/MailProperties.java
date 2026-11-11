package com.villagebooker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// resendApiKey empty = nothing is sent, emails stay PENDING in email_message.
// redirectTo (development): every email goes to this address instead of the recipient; without its own domain
// Resend delivers only to the address of the Resend account.
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(String resendApiKey, String from, String redirectTo) {
}
