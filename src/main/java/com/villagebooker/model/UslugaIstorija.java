package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Data
@Table(name="uslugaistorija")
@AllArgsConstructor
@NoArgsConstructor
public class UslugaIstorija {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usluga_id")
    private Usluga usluga;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="staripodaci", columnDefinition = "jsonb")
    private Map<String, Object> staripodaci;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="novipodaci", columnDefinition = "jsonb")
    private Map<String, Object> novipodaci;

    private LocalDateTime vreme_promene;
    private String uzrok;
}
