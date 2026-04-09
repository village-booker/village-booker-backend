package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name="omiljenausluga")
@AllArgsConstructor
@NoArgsConstructor
public class OmiljenaUsluga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "korisnik_id")
    private Korisnik korisnik;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usluga_id")
    private Usluga usluga;

    private LocalDateTime datum_dodavanja = LocalDateTime.now();
    private LocalDateTime datum_brisanja;
    
    @Enumerated(EnumType.STRING)
    private StatusOmiljene status;
}
