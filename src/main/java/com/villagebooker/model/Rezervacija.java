package com.villagebooker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name="rezervacija")
@AllArgsConstructor
@NoArgsConstructor
public class Rezervacija {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @CreationTimestamp
    @Column(name = "vreme_rezervisanja", nullable = false)
    private LocalDateTime vremeRezervisanja;
    
    @Column(name = "ukupna_cena", precision = 19, scale = 2, nullable = false)
    private BigDecimal ukupnaCena;
    
    @Column(name = "preostalo_za_platiti", precision = 19, scale = 2, nullable = false)
    private BigDecimal preostaloZaPlatiti;
    
    @Column(name = "rezervacija_od", nullable = false)
    private LocalDate rezervacijaOd;
    
    @Column(name = "rezervacija_do", nullable = false)
    private LocalDate rezervacijaDo;
    
    @Column(name = "broj_dana", nullable = false)
    private Integer brojDana;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusRezervacije status;
    
    @Column(name = "br_osoba", nullable = false)
    private Integer brOsoba;

    @Column(name = "napomena")
    private String napomena;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gost_id")
    private Korisnik gost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vlasnik_id")
    private Korisnik vlasnik;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usluga_id")
    private Usluga usluga;

    @OneToMany(mappedBy = "rezervacija", fetch = FetchType.LAZY)
    private List<Placanje> placanja;

    @OneToOne(mappedBy = "rezervacija", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Recenzija recenzija;
}
