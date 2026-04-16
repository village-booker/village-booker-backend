package com.villagebooker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(name = "tura")
@AllArgsConstructor
@NoArgsConstructor
public class Tura extends Usluga {

    @Column(name = "broj_dana")
    private int brojDana;

    @Enumerated(EnumType.STRING)
    @Column(name = "tezina_ture")
    private TezinaTure tezinaTure;

    @Column(precision = 19, scale = 2)
    private BigDecimal cena;
}
