package com.villagebooker.model;

import com.villagebooker.enums.StatusTermina;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "termin_ture", indexes = {
        @Index(name = "idx_termin_tura_datum", columnList = "tura_id, datum_od")
})
@AllArgsConstructor
@NoArgsConstructor
public class TerminTure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

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
    private LocalDateTime rokZaMin;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tura_id", nullable = false)
    private Tura tura;

    @OneToMany(mappedBy = "termin", fetch = FetchType.LAZY)
    private List<Rezervacija> rezervacije;
}
