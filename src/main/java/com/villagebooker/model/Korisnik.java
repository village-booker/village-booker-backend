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
    @Column(name = "korisnik_id")
    private Long korisnikId;

    private String ime;
    private String prezime;
    private String email;
    private String sifra;
    private String telefon;
    private String slikaUrl;
    
    @CreationTimestamp
    @Column(name = "kreiran_at")
    private LocalDateTime kreiranAt;
    
    @Column(name = "bankovni_racun")
    private String bankovniRacun;
    
    @Enumerated(EnumType.STRING)
    private Uloga uloga;
    
    @OneToMany(mappedBy = "vlasnik", fetch = FetchType.LAZY)
    private List<Usluga> usluge;

    @OneToMany(mappedBy = "korisnik", fetch = FetchType.LAZY)
    private List<Rezervacija> rezervacije;

    @OneToMany(mappedBy = "korisnik", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OmiljenaUsluga> omiljeneUsluge;
}
