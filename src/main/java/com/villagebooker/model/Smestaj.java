package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name="smestaj")
@AllArgsConstructor
@NoArgsConstructor
public class Smestaj extends Usluga{

    private float cena_po_danu;
    private int broj_francusko_lezaj;
    private int broj_kreveta_solo;
    private int broj_soba;
}
