package com.villagebooker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// emails are sent on a background thread after commit, so the request does not wait for Resend
@Configuration
@EnableAsync
public class AsyncConfig {
}
