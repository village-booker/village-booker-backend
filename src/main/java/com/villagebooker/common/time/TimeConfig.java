package com.villagebooker.common.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {

    // services use Instant.now(clock) / AppTime.today(clock); tests replace it with Clock.fixed(...)
    @Bean
    public Clock clock() {
        return Clock.system(AppTime.ZONE);
    }
}
