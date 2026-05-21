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
@DiscriminatorValue("SMESTAJ")
@PrimaryKeyJoinColumn(name = "usluga_id")
public class Smestaj extends Usluga {

    @Column(name = "bazna_cena_noci", precision = 19, scale = 2, nullable = false)
    private BigDecimal baznaCenaNoci;

    @Column(name = "bazan_br_osoba", nullable = false)
    private Integer bazanBrOsoba;

    @Column(name = "dodatna_cena_osoba", precision = 19, scale = 2, nullable = false)
    private BigDecimal dodatnaCenaOsoba;

    @Column(name = "broj_francuski_lezaj", nullable = false)
    private Integer brojFrancuskihLezaja;
    
    @Column(name = "broj_kreveta_solo", nullable = false)
    private Integer brojKrevetaSolo;
    
    @Column(name = "broj_soba", nullable = false)
    private Integer brojSoba;
}
