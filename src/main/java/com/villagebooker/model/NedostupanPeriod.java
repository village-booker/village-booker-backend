package com.villagebooker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name="nedostupanperiod")
@AllArgsConstructor
@NoArgsConstructor
public class NedostupanPeriod {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "datum_od", nullable = false)
    private LocalDateTime datum_od;

    @Column(name = "datum_do", nullable = false)
    private LocalDateTime datum_do;

    @Column(name = "razlog")
    private String razlog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usluga_id", nullable = false)
    private Usluga usluga;
}
