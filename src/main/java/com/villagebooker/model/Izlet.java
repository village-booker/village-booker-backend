package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name="izlet")
@AllArgsConstructor
@NoArgsConstructor
public class Izlet extends Usluga{

    private int broj_dana;
    private int tezina;
    private float cena;
}
