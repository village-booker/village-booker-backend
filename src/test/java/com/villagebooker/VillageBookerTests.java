package com.villagebooker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest
// same configuration as the other tests, so they share one Spring context
@AutoConfigureMockMvc
class VillageBookerTests {

    @Test
    void contextLoads() {
    }

}
