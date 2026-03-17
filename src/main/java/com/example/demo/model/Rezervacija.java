package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity @Data @Table(name="rezervacija") @AllArgsConstructor @NoArgsConstructor
public class Rezervacija {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int rezervacija_id;

    private LocalDateTime vreme_rezervisanja = LocalDateTime.now();
    private float ukupna_cena;
    private LocalDateTime rezervacija_od;
    private LocalDateTime rezervacija_do;
    private int broj_dana;
    private StatusRezervacije status;
    private int br_osoba;
    private String napomena;
}
