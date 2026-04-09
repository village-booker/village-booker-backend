package com.villagebooker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name="omiljenausluga")
@AllArgsConstructor
@NoArgsConstructor
public class OmiljenaUsluga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "korisnik_id")
    private Korisnik korisnik;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usluga_id")
    private Usluga usluga;

    @CreationTimestamp
    @Column(name = "datum_dodavanja")
    private LocalDateTime datumDodavanja;
    
    @Column(name = "datum_brisanja")
    private LocalDateTime datumBrisanja;
    
    @Enumerated(EnumType.STRING)
    private StatusOmiljene status;
}
