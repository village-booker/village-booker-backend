package com.villagebooker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name="karakteristika")
public class Karakteristika {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name= "id")
    private Long id;

    @Column(unique = true, nullable = false, name = "naziv")
    private String naziv;

    @ManyToMany(mappedBy = "karakteristike", fetch = FetchType.LAZY)
    private List<Usluga> usluge;
}
