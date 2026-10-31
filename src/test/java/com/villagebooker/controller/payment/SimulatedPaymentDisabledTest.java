package com.villagebooker.controller.payment;

import com.villagebooker.service.payment.SimulatedPaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

// production setting: the simulated payment must not exist at all
@SpringBootTest(properties = "app.payments.simulated=false")
class SimulatedPaymentDisabledTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void noPaymentBeansWithoutTheSetting() {
        assertThat(context.getBeanNamesForType(SimulatedPaymentController.class)).isEmpty();
        assertThat(context.getBeanNamesForType(SimulatedPaymentService.class)).isEmpty();
    }
}
