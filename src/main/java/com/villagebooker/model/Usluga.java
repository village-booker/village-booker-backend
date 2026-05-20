package com.villagebooker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name="usluga")
@AllArgsConstructor
@NoArgsConstructor
@Inheritance(strategy = InheritanceType.JOINED)
public class Usluga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "naziv")
    private String naziv;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusUsluge status;

    @Column(name = "opis")
    private String opis;
    
    @Column(name = "maks_br_osoba")
    private Integer maksBrOsoba;
    
    @Column(name = "min_br_osoba")
    private Integer minBrOsoba;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "tip_usluge")
    private TipUsluge tipUsluge;
    
    @Column(name = "broj_recenzija")
    private Integer brojRecenzija;

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

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal  longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "politika_otkazivanja")
    private PolitikaOtkazivanja politikaOtkazivanja;

    @Enumerated(EnumType.STRING)
    @Column(name = "nacin_placanja")
    private NacinPlacanja nacinPlacanja;

    @Column(name = "kapara_procenat")
    private BigDecimal kaparaProcenat;

    @Column(name = "rok_besplatnog_otkazivanja")
    private Integer rokBesplatnogOtkazivanja;

    @Column(name = "procenat_povrata")
    private BigDecimal procenatPovrata;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "korisnik_id")
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
