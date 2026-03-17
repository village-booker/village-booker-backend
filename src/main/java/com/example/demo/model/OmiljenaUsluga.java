package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity @Data @Table(name="omiljenausluga") @AllArgsConstructor @NoArgsConstructor
public class OmiljenaUsluga {

    @Id
    private int korisnik_id;
    @Id
    private int usluga_id;

    private LocalDateTime datum_dodavanja = LocalDateTime.now();
    private LocalDateTime datum_brisanja;
    private StatusOmiljene status;

}
