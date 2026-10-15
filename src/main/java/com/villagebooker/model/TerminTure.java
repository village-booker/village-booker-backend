package com.villagebooker.model;

import com.villagebooker.enums.StatusTermina;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "termin_ture",
        indexes = {
                @Index(name = "idx_termin_tura_datum", columnList = "tura_id, datum_od")
        },
        check = {
                @CheckConstraint(name = "chk_termin_datumi", constraint = "datum_od <= datum_do"),
                @CheckConstraint(name = "chk_termin_br_prijavljenih", constraint = "br_prijavljenih >= 0")
        })
@AllArgsConstructor
@NoArgsConstructor
public class TerminTure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // zatvoren interval [datumOd, datumDo]: datumDo = datumOd + tura.brojDana - 1 (poslednji dan ture)
    @Column(name = "datum_od", nullable = false)
    private LocalDate datumOd;

    @Column(name = "datum_do", nullable = false)
    private LocalDate datumDo;

    @Column(name = "br_prijavljenih", nullable = false)
    private Integer brPrijavljenih = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusTermina status = StatusTermina.OTVOREN;

    @Column(name = "rok_za_min", nullable = false)
    private Instant rokZaMin;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tura_id", nullable = false)
    private Tura tura;
}
