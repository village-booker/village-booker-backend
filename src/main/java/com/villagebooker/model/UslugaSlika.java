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
@Table(name="uslugaslika")
@AllArgsConstructor
@NoArgsConstructor
public class UslugaSlika {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usluga_id")
    private Usluga usluga;

    private String url;
    private int redosled;
    
    @CreationTimestamp
    @Column(name = "kreirana_at")
    private LocalDateTime kreiranaAt;
}
