package com.villagebooker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name="korisnik")
@AllArgsConstructor
@NoArgsConstructor
public class Korisnik {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ime")
    private String ime;

    @Column(name = "prezime")
    private String prezime;

    @Column(name = "email")
    private String email;

    @Column(name = "sifra")
    private String sifra;

    @Column(name = "telefon")
    private String telefon;

    @Column(name = "slika_url")
    private String slikaUrl;
    
    @CreationTimestamp
    @Column(name = "kreiran_at")
    private LocalDateTime kreiranAt;
    
    @Column(name = "bankovni_racun")
    private String bankovniRacun;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "uloga")
    private Uloga uloga;
    
    @OneToMany(mappedBy = "vlasnik", fetch = FetchType.LAZY)
    private List<Usluga> usluge;

    @OneToMany(mappedBy = "gost", fetch = FetchType.LAZY)
    private List<Rezervacija> napravljeneRezervacije;

    @OneToMany(mappedBy = "vlasnik", fetch = FetchType.LAZY)
    private List<Rezervacija> primljeneRezervacije;

    @OneToMany(mappedBy = "korisnik", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OmiljenaUsluga> omiljeneUsluge;

    @OneToMany(mappedBy = "podnosilac", fetch = FetchType.LAZY)
    private List<ZahtevZaIzmenuUsluge> podnetiZahtevi;

    @OneToMany(mappedBy = "admin", fetch = FetchType.LAZY)
    private List<ZahtevZaIzmenuUsluge> obradjenizahtevi;
}
