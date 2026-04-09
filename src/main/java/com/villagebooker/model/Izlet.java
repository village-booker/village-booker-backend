package com.villagebooker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name="izlet")
@AllArgsConstructor
@NoArgsConstructor
public class Izlet extends Usluga {

    @Column(name = "broj_dana")
    private int brojDana;
    
    private int tezina;
    private float cena;
}
