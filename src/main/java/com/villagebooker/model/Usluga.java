package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table(name="usluga")
@AllArgsConstructor
@NoArgsConstructor
@Inheritance(strategy = InheritanceType.JOINED)
public class Usluga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int usluga_id;

    private String naziv;
    
    @Enumerated(EnumType.STRING)
    private StatusUsluge status;
    
    private String opis;
    private int maks_br_osoba;
    private int min_br_osoba;
    
    @Enumerated(EnumType.STRING)
    private Tip tip;
    
    private int broj_recenzija;
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
    private java.util.List<UslugaSlika> slike;

    @OneToMany(mappedBy = "usluga", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<UslugaIstorija> istorija;

    @OneToMany(mappedBy = "usluga", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private java.util.List<Rezervacija> rezervacije;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "usluga_karakteristika",
        joinColumns = @JoinColumn(name = "usluga_id"),
        inverseJoinColumns = @JoinColumn(name = "karakteristika_id")
    )
    private java.util.List<Karakteristika> karakteristike;

}
