package com.villagebooker.repo;

import com.villagebooker.model.Feature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeatureRepo extends JpaRepository<Feature, Long> {

    Optional<Feature> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
}
