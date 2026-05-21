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
@Table(name="recenzija")
@AllArgsConstructor
@NoArgsConstructor
public class Recenzija {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ocena")
    private Integer ocena;

    @Column(name = "komentar")
    private String komentar;

    @Column(name = "odgovor")
    private String odgovor;

    @CreationTimestamp
    @Column(name = "datum", nullable = false)
    private LocalDateTime datum;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rezervacija_id", nullable = false, unique = true)
    private Rezervacija rezervacija;
}
