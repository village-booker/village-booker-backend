package com.villagebooker.model;

import com.villagebooker.enums.NacinPlacanja;
import com.villagebooker.enums.PolitikaOtkazivanja;
import com.villagebooker.enums.StatusRezervacije;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "rezervacija",
        indexes = {
                @Index(name = "idx_rezervacija_usluga", columnList = "usluga_id"),
                @Index(name = "idx_rezervacija_gost", columnList = "gost_id"),
                @Index(name = "idx_rezervacija_termin", columnList = "termin_id"),
                @Index(name = "idx_rezervacija_status_istice", columnList = "status, istice_u")
        },
        check = {
                @CheckConstraint(name = "chk_rezervacija_datumi", constraint = "rezervacija_od < rezervacija_do"),
                @CheckConstraint(name = "chk_rezervacija_br_osoba", constraint = "br_osoba > 0"),
                @CheckConstraint(name = "chk_rezervacija_procenat_povrata", constraint = "procenat_povrata BETWEEN 0 AND 100"),
                @CheckConstraint(name = "chk_rezervacija_kapara_procenat", constraint = "kapara_procenat BETWEEN 0 AND 100")
        })
@AllArgsConstructor
@NoArgsConstructor
public class Rezervacija {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @CreationTimestamp
    @Column(name = "vreme_rezervisanja", nullable = false, updatable = false)
    private Instant vremeRezervisanja;

    @Column(name = "ukupna_cena", precision = 19, scale = 2, nullable = false)
    private BigDecimal ukupnaCena;

    @Column(name = "preostalo_za_platiti", precision = 19, scale = 2, nullable = false)
    private BigDecimal preostaloZaPlatiti;

    @Column(name = "rezervacija_od", nullable = false)
    private LocalDate rezervacijaOd;

    @Column(name = "rezervacija_do", nullable = false)
    private LocalDate rezervacijaDo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusRezervacije status;

    // rok za status koji moze da istekne (CEKA_POTVRDU_VLASNIKA, CEKA_PLACANJE); cita ga scheduler
    @Column(name = "istice_u")
    private Instant isticeU;

    @Column(name = "br_osoba", nullable = false)
    private Integer brOsoba;

    @Column(name = "napomena")
    private String napomena;

    @Enumerated(EnumType.STRING)
    @Column(name = "politika_otkazivanja", nullable = false)
    private PolitikaOtkazivanja politikaOtkazivanja;

    @Column(name = "besplatno_otkazivanje_do")
    private Instant besplatnoOtkazivanjeDo;

    @Column(name = "procenat_povrata", precision = 5, scale = 2)
    private BigDecimal procenatPovrata;

    @Column(name = "kapara_procenat", precision = 5, scale = 2)
    private BigDecimal kaparaProcenat;

    @Column(name = "otkazano_at")
    private LocalDateTime otkazanoAt;

    @Column(name = "razlog_otkazivanja")
    private String razlogOtkazivanja;

    @Enumerated(EnumType.STRING)
    @Column(name = "nacin_placanja", nullable = false)
    private NacinPlacanja nacinPlacanja;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gost_id", nullable = false)
    private Korisnik gost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usluga_id", nullable = false)
    private Usluga usluga;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "termin_id")
    private TerminTure termin;

    @OneToMany(mappedBy = "rezervacija", fetch = FetchType.LAZY)
    private List<Placanje> placanja;

    @OneToOne(mappedBy = "rezervacija", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Recenzija recenzija;

    public long getBrojNoci() {
        return ChronoUnit.DAYS.between(rezervacijaOd, rezervacijaDo);
    }
}
