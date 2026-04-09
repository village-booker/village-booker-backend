package com.villagebooker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name="rezervacija")
@AllArgsConstructor
@NoArgsConstructor
public class Rezervacija {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rezervacija_id")
    private Long rezervacijaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "korisnik_id")
    private Korisnik korisnik;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usluga_id")
    private Usluga usluga;

    @CreationTimestamp
    @Column(name = "vreme_rezervisanja")
    private LocalDateTime vremeRezervisanja;
    
    @Column(name = "ukupna_cena")
    private float ukupnaCena;
    
    @Column(name = "preostalo_za_platiti")
    private float preostaloZaPlatiti;
    
    @Column(name = "rezervacija_od")
    private LocalDate rezervacijaOd;
    
    @Column(name = "rezervacija_do")
    private LocalDate rezervacijaDo;
    
    @Column(name = "broj_dana")
    private int brojDana;
    
    @Enumerated(EnumType.STRING)
    private StatusRezervacije status;
    
    @Column(name = "br_osoba")
    private int brOsoba;
    
    private String napomena;
}
