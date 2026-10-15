package com.villagebooker.model;

import com.villagebooker.enums.Uloga;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

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

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "sifra", nullable = false)
    private String sifra;

    @Column(name = "telefon")
    private String telefon;

    @Column(name = "slika_url")
    private String slikaUrl;
    
    @CreationTimestamp
    @Column(name = "kreiran_at", nullable = false, updatable = false)
    private Instant kreiranAt;
    
    @Column(name = "bankovni_racun")
    private String bankovniRacun;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "uloga", nullable = false)
    private Uloga uloga;
}
