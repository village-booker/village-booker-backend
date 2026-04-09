package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity @Data @Table(name="usluga") @AllArgsConstructor @NoArgsConstructor
public abstract class Usluga {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int usluga_id;

    private String naziv;
    private StatusUsluge status;
    private String opis;
    private int maks_br_osoba;
    private int min_br_osoba;
    private TipUsluge tip;
    private int broj_recenzija;
    private String adresa;
    private String mesto;
    private String opstina;
    private String okrug;
    private float ocena;
    private float latitude;
    private float longitude;
    private PolitikaOtkazivanja politika_otkazivanja;
    private NacinPlacanja nacin_placanja;
    private float kapara_procenat;
    private LocalDateTime rok_besplatnog_otkazivanja;
    private float procenat_povrata;
}
