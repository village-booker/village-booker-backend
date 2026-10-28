package com.villagebooker.repo.payment;

import com.villagebooker.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepo extends JpaRepository<Payment, Long> {

    List<Payment> findByBookingId(Long bookingId);

    // for the future Stripe webhook
    Optional<Payment> findByStripePaymentId(String stripePaymentId);
}
