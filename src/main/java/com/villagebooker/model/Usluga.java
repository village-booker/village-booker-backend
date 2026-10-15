package com.villagebooker.model;

import com.villagebooker.enums.NacinPlacanja;
import com.villagebooker.enums.PolitikaOtkazivanja;
import com.villagebooker.enums.StatusUsluge;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "usluga",
        indexes = {
                @Index(name = "idx_usluga_vlasnik", columnList = "vlasnik_id"),
                @Index(name = "idx_usluga_okrug", columnList = "okrug"),
                @Index(name = "idx_usluga_status", columnList = "status")
        },
        check = {
                @CheckConstraint(name = "chk_usluga_br_osoba", constraint = "min_br_osoba > 0 AND min_br_osoba <= maks_br_osoba"),
                @CheckConstraint(name = "chk_usluga_kapara_procenat", constraint = "kapara_procenat BETWEEN 0 AND 100"),
                @CheckConstraint(name = "chk_usluga_procenat_povrata", constraint = "procenat_povrata BETWEEN 0 AND 100"),
                @CheckConstraint(name = "chk_usluga_rok_otkazivanja", constraint = "rok_besplatnog_otkazivanja >= 0")
        })
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "tip_usluge", discriminatorType = DiscriminatorType.STRING)
public abstract class Usluga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "naziv", nullable = false)
    private String naziv;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusUsluge status;

    @Column(name = "opis")
    private String opis;

    @Column(name = "zahteva_potvrdu_vlasnika", nullable = false)
    private boolean zahtevaPotvrduVlasnika = false;

    @Column(name = "maks_br_osoba", nullable = false)
    private Integer maksBrOsoba;

    @Column(name = "min_br_osoba", nullable = false)
    private Integer minBrOsoba;

    @Column(name = "broj_recenzija", nullable = false)
    private Integer brojRecenzija = 0;

    @Column(name = "adresa")
    private String adresa;

    @Column(name = "mesto")
    private String mesto;

    @Column(name = "opstina")
    private String opstina;

    @Column(name = "okrug")
    private String okrug;

    @Column(name = "ocena", precision = 3, scale = 2)
    private BigDecimal ocena;

    @Column(name = "latitude", precision = 9, scale = 6, nullable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6, nullable = false)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "politika_otkazivanja", nullable = false)
    private PolitikaOtkazivanja politikaOtkazivanja;

    @Enumerated(EnumType.STRING)
    @Column(name = "nacin_placanja", nullable = false)
    private NacinPlacanja nacinPlacanja;

    @Column(name = "kapara_procenat", precision = 5, scale = 2)
    private BigDecimal kaparaProcenat;

    @Column(name = "rok_besplatnog_otkazivanja")
    private Integer rokBesplatnogOtkazivanja;

    @Column(name = "procenat_povrata", precision = 5, scale = 2)
    private BigDecimal procenatPovrata;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vlasnik_id", nullable = false)
    private Korisnik vlasnik;

    @OneToMany(mappedBy = "usluga", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UslugaSlika> slike;

    @OneToMany(mappedBy = "usluga", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NedostupanPeriod> nedostupniPeriodi;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "usluga_karakteristika",
            joinColumns = @JoinColumn(name = "usluga_id"),
            inverseJoinColumns = @JoinColumn(name = "karakteristika_id")
    )
    private Set<Karakteristika> karakteristike = new HashSet<>();
}