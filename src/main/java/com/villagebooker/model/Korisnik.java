package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name="korisnik")
@AllArgsConstructor
@NoArgsConstructor
public class Korisnik {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int korisnik_id;

    private String ime;
    private String prezime;
    private String email;
    private String sifra;
    private String telefon;
    private String slikaUrl;
    private LocalDateTime kreiran_at = LocalDateTime.now();
    private String bankovni_racun;
    
    @Enumerated(EnumType.STRING)
    private Uloga uloga;
    
    @OneToMany(mappedBy = "vlasnik", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private java.util.List<Usluga> usluge;

    @OneToMany(mappedBy = "korisnik", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private java.util.List<Rezervacija> rezervacije;

    @OneToMany(mappedBy = "korisnik", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<OmiljenaUsluga> omiljeneUsluge;
}
