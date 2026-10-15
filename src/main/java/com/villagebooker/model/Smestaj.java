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
@Table(name = "smestaj", check = {
        @CheckConstraint(name = "chk_smestaj_bazna_cena", constraint = "bazna_cena_noci > 0"),
        @CheckConstraint(name = "chk_smestaj_dodatna_cena", constraint = "dodatna_cena_osoba >= 0"),
        @CheckConstraint(name = "chk_smestaj_bazan_br_osoba", constraint = "bazan_br_osoba >= 1"),
        @CheckConstraint(name = "chk_smestaj_lezajevi", constraint = "broj_francuski_lezaj >= 0 AND broj_kreveta_solo >= 0"),
        @CheckConstraint(name = "chk_smestaj_broj_soba", constraint = "broj_soba >= 1")
})
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
