package com.villagebooker.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "usluga", indexes = {
        @Index(name = "idx_usluga_vlasnik", columnList = "vlasnik_id"),
        @Index(name = "idx_usluga_okrug", columnList = "okrug"),
        @Index(name = "idx_usluga_status", columnList = "status")
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
    private boolean zahtevaPotvrdruVlasnika = false;

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

    @Column(name = "ocena")
    private BigDecimal ocena;

    @Column(name = "latitude", nullable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "politika_otkazivanja", nullable = false)
    private PolitikaOtkazivanja politikaOtkazivanja;

    @Enumerated(EnumType.STRING)
    @Column(name = "nacin_placanja", nullable = false)
    private NacinPlacanja nacinPlacanja;

    @Column(name = "kapara_procenat")
    private BigDecimal kaparaProcenat;

    @Column(name = "rok_besplatnog_otkazivanja")
    private Integer rokBesplatnogOtkazivanja;

    @Column(name = "procenat_povrata")
    private BigDecimal procenatPovrata;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vlasnik_id", nullable = false)
    private Korisnik vlasnik;

    @OneToMany(mappedBy = "usluga", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UslugaSlika> slike;

    @OneToMany(mappedBy = "usluga", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UslugaIstorija> istorija;

    @OneToMany(mappedBy = "usluga", fetch = FetchType.LAZY)
    private List<Rezervacija> rezervacije;

    @OneToMany(mappedBy = "usluga", fetch = FetchType.LAZY)
    private List<ZahtevZaIzmenuUsluge> zahtevi;

    @OneToMany(mappedBy = "usluga", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NedostupanPeriod> nedostupniPeriodi;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "usluga_karakteristika",
            joinColumns = @JoinColumn(name = "usluga_id"),
            inverseJoinColumns = @JoinColumn(name = "karakteristika_id")
    )
    private List<Karakteristika> karakteristike;
}