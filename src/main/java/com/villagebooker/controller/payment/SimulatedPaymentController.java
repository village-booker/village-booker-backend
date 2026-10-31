package com.villagebooker.controller.payment;

import com.villagebooker.dto.booking.BookingResponse;
import com.villagebooker.security.AuthUser;
import com.villagebooker.service.payment.SimulatedPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

// without app.payments.simulated=true the route does not exist (404)
@RestController
@ConditionalOnProperty(name = "app.payments.simulated", havingValue = "true")
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
public class SimulatedPaymentController {

    private final SimulatedPaymentService simulatedPaymentService;

    @PostMapping("/api/bookings/{id}/pay")
    public BookingResponse payDeposit(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
        return simulatedPaymentService.payDeposit(authUser.id(), id);
    }
}
