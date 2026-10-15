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
@Table(name = "recenzija", check = {
        @CheckConstraint(name = "chk_recenzija_ocena", constraint = "ocena BETWEEN 1 AND 5")
})
@AllArgsConstructor
@NoArgsConstructor
public class Recenzija {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ocena", nullable = false)
    private Integer ocena;

    @Column(name = "komentar")
    private String komentar;

    @Column(name = "odgovor")
    private String odgovor;

    @CreationTimestamp
    @Column(name = "datum", nullable = false, updatable = false)
    private Instant datum;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rezervacija_id", nullable = false, unique = true)
    private Rezervacija rezervacija;
}
