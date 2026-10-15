package com.villagebooker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(name = "nedostupanperiod",
        indexes = {
                @Index(name = "idx_nedostupan_period_usluga", columnList = "usluga_id, datum_od")
        },
        check = {
                @CheckConstraint(name = "chk_nedostupan_period_datumi", constraint = "datum_od <= datum_do")
        })
@AllArgsConstructor
@NoArgsConstructor
public class NedostupanPeriod {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // zatvoren interval [datumOd, datumDo]: svaki datum je jedna blokirana noc
    @Column(name = "datum_od", nullable = false)
    private LocalDate datumOd;

    @Column(name = "datum_do", nullable = false)
    private LocalDate datumDo;

    @Column(name = "razlog")
    private String razlog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usluga_id", nullable = false)
    private Usluga usluga;
}
