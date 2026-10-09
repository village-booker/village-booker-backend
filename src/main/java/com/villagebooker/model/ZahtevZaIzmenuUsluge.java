package com.villagebooker.model;

import com.villagebooker.enums.StatusZahteva;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Getter
@Setter
@Table(name = "zahtevzaizmenuusluge", indexes = {
        @Index(name = "idx_zahtev_usluga", columnList = "usluga_id"),
        @Index(name = "idx_zahtev_podnosilac", columnList = "podnosilac_id"),
        @Index(name = "idx_zahtev_status", columnList = "status")
})
@AllArgsConstructor
@NoArgsConstructor
public class ZahtevZaIzmenuUsluge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "podaci_za_izmenu", columnDefinition = "jsonb")
    private Map<String, Object> podaciZaIzmenu;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusZahteva status;

    @CreationTimestamp
    @Column(name = "kreiran_at", nullable = false, updatable = false)
    private Instant kreiranAt;

    @Column(name = "obradjen_at")
    private Instant obradjenAt;

    @Column(name = "razlog_odbijanja")
    private String razlogOdbijanja;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "podnosilac_id", nullable = false)
    private Korisnik podnosilac;

    // null dok admin ne obradi zahtev
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private Korisnik admin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usluga_id", nullable = false)
    private Usluga usluga;
}
