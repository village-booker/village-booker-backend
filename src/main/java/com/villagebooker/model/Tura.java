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

    @Column(name = "broj_dana", nullable = false)
    private Integer brojDana;

    @Enumerated(EnumType.STRING)
    @Column(name = "tezina_ture", nullable = false)
    private TezinaTure tezinaTure;

    @Column(precision = 19, scale = 2, nullable = false, name= "cena_po_osobi")
    private BigDecimal cenaPoOsobi;
}
