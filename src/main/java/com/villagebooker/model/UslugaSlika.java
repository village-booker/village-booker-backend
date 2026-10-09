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
@Table(name = "uslugaslika", indexes = {
        @Index(name = "idx_usluga_slika_usluga", columnList = "usluga_id, redosled")
})
@AllArgsConstructor
@NoArgsConstructor
public class UslugaSlika {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usluga_id", nullable = false)
    private Usluga usluga;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "redosled", nullable = false)
    private Integer redosled;
    
    @CreationTimestamp
    @Column(name = "kreirana_at", nullable = false)
    private Instant kreiranaAt;
}
