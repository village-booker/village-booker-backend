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
@Table(name="smestaj")
@AllArgsConstructor
@NoArgsConstructor
public class Smestaj extends Usluga {

    @Column(name = "cena_po_danu", precision = 19, scale = 2)
    private BigDecimal cenaPoNoci;
    
    @Column(name = "broj_francuski_lezaj")
    private int brojFrancuskihLezaja;
    
    @Column(name = "broj_kreveta_solo")
    private int brojKrevetaSolo;
    
    @Column(name = "broj_soba")
    private int brojSoba;
}
