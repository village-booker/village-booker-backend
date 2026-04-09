package com.villagebooker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    @Column(name = "usluga_id")
    private Long uslugaId;

    private String naziv;
    
    @Enumerated(EnumType.STRING)
    private StatusUsluge status;
    
    private String opis;
    
    @Column(name = "maks_br_osoba")
    private int maksBrOsoba;
    
    @Column(name = "min_br_osoba")
    private int minBrOsoba;
    
    @Enumerated(EnumType.STRING)
    private TipUsluge tipUsluge;
    
    @Column(name = "broj_recenzija")
    private int brojRecenzija;

    @Enumerated(EnumType.STRING)
    @Column(name = "politika_otkazivanja")
    private PolitikaOtkazivanja politikaOtkazivanja;

    @Enumerated(EnumType.STRING)
    @Column(name = "nacin_placanja")
    private NacinPlacanja nacinPlacanja;

    @Column(name = "kapara_procenat")
    private Integer kaparaProcenat;

    @Column(name = "rok_besplatnog_otkazivanja")
    private Integer rokBesplatnogOtkazivanja;

    @Column(name = "procenat_povrata")
    private Integer procentatPovrata;

    private String adresa;
    private String mesto;
    private String opstina;
    private String okrug;
    private float ocena;
    private float latitude;
    private float longitude;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vlasnik_id")
    private Korisnik vlasnik;

    @OneToMany(mappedBy = "usluga", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UslugaSlika> slike;

    @OneToMany(mappedBy = "usluga", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UslugaIstorija> istorija;

    @OneToMany(mappedBy = "usluga", fetch = FetchType.LAZY)
    private List<Rezervacija> rezervacije;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "usluga_karakteristika",
        joinColumns = @JoinColumn(name = "usluga_id"),
        inverseJoinColumns = @JoinColumn(name = "karakteristika_id")
    )
    private List<Karakteristika> karakteristike;

}
