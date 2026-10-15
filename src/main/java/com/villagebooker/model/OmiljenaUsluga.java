package com.villagebooker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "omiljenausluga",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_omiljena_korisnik_usluga", columnNames = {"korisnik_id", "usluga_id"})
        },
        indexes = {
                @Index(name = "idx_omiljena_usluga", columnList = "usluga_id")
        })
@AllArgsConstructor
@NoArgsConstructor
public class OmiljenaUsluga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "korisnik_id", nullable = false)
    private Korisnik korisnik;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usluga_id", nullable = false)
    private Usluga usluga;

    @CreationTimestamp
    @Column(name = "datum_dodavanja", nullable = false, updatable = false)
    private Instant datumDodavanja;
}