package com.villagebooker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Catalog of listing features (WiFi, parking, pool...), managed by the admin.
// The name is shown in the app's language, so it is stored in both languages (not translated by the frontend,
// because the admin adds features at runtime).
@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "feature")
public class Feature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name_sr", nullable = false, unique = true, length = 100)
    private String nameSr;

    @Column(name = "name_en", nullable = false, unique = true, length = 100)
    private String nameEn;
}
