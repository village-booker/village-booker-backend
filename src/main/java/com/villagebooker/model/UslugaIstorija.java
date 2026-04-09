package com.villagebooker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usluga_id")
    private Usluga usluga;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="staripodaci", columnDefinition = "jsonb")
    private Map<String, Object> stariPodaci;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="novipodaci", columnDefinition = "jsonb")
    private Map<String, Object> noviPodaci;

    @Column(name = "vreme_promene")
    private LocalDateTime vremePromene;
    
    private String uzrok;
}
