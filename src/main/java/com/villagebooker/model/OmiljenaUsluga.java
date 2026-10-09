package com.villagebooker.model;

import com.villagebooker.enums.StatusOmiljene;
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
@Table(name = "omiljenausluga", indexes = {
        @Index(name = "idx_omiljena_korisnik", columnList = "korisnik_id"),
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
    @Column(name = "datum_dodavanja", nullable = false)
    private Instant datumDodavanja;
    
    @Column(name = "datum_brisanja")
    private Instant datumBrisanja;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusOmiljene status;
}
