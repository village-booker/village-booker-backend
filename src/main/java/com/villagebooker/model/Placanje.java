package com.villagebooker.model;

import com.villagebooker.enums.NacinUplate;
import com.villagebooker.enums.StatusPlacanja;
import com.villagebooker.enums.TipPlacanja;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "placanje",
        indexes = {
                @Index(name = "idx_placanje_rezervacija", columnList = "rezervacija_id")
        },
        check = {
                @CheckConstraint(name = "chk_placanje_iznos", constraint = "iznos > 0")
        })
@NoArgsConstructor
@AllArgsConstructor
public class Placanje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "iznos", precision = 19, scale = 2, nullable = false)
    private BigDecimal iznos;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusPlacanja status;

    @Column(name = "stripe_payment_id")
    private String stripePaymentId;

    @Column(name = "stripe_refund_id")
    private String stripeRefundId;

    @CreationTimestamp
    @Column(name = "vreme", nullable = false, updatable = false)
    private Instant vreme;

    @Enumerated(EnumType.STRING)
    @Column(name = "nacin", nullable = false)
    private NacinUplate nacin;

    @Enumerated(EnumType.STRING)
    @Column(name = "tip_placanja", nullable = false)
    private TipPlacanja tipPlacanja;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rezervacija_id", nullable = false)
    private Rezervacija rezervacija;
}
