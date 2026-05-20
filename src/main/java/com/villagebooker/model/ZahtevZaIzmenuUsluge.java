package com.villagebooker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Getter
@Setter
@Table(name = "zahtevzaizmenuusluge")
@AllArgsConstructor
@NoArgsConstructor
public class ZahtevZaIzmenuUsluge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="podaci_za_izmenu", columnDefinition = "jsonb")
    private Map<String, Object> podaciZaIzmenu;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusZahteva status;

    @CreationTimestamp
    @Column(name = "kreiran_at")
    private LocalDateTime kreiran_at;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "podnosilac_id", nullable = false)
    private Korisnik podnosilac;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private Korisnik admin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usluga_id", nullable = false)
    private Usluga usluga;
}
