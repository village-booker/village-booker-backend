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
@Table(name="uslugaistorija")
@AllArgsConstructor
@NoArgsConstructor
public class UslugaIstorija {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usluga_id", nullable = false)
    private Usluga usluga;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="staro_stanje", columnDefinition = "jsonb")
    private Map<String, Object> staroStanje;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="novo_stanje", columnDefinition = "jsonb")
    private Map<String, Object> novoStanje;

    @CreationTimestamp
    @Column(name = "vreme_promene", nullable = false)
    private LocalDateTime vremePromene;

    @Column(name = "uzrok")
    private String uzrok;
}
