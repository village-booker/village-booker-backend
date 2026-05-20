package com.villagebooker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "placanje")
@NoArgsConstructor
@AllArgsConstructor
public class Placanje {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "iznos")
    private BigDecimal iznos;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusPlacanja status;

    @Column(name = "stripe_payment_id")
    private String stripePaymentId;

    @Column(name = "stripe_refund_id")
    private String StripeRefundId;

    @CreationTimestamp
    @Column(name = "vreme")
    private LocalDateTime vreme;

    @Enumerated(EnumType.STRING)
    @Column(name = "nacin")
    private NacinPlacanja nacin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rezervacija_id")
    private Rezervacija rezervacija;
}
