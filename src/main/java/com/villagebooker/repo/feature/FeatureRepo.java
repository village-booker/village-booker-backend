package com.villagebooker.repo.feature;

import com.villagebooker.model.Feature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FeatureRepo extends JpaRepository<Feature, Long> {

    List<Feature> findAllByOrderByNameSr();

    List<Feature> findByIdIn(Collection<Long> ids);

    // the catalog is small, so the case-insensitive comparison without an index is fine
    boolean existsByNameSrIgnoreCaseOrNameEnIgnoreCase(String nameSr, String nameEn);

    @Query("""
            select count(f) > 0 from Feature f
            where f.id <> :id and (lower(f.nameSr) = lower(:nameSr) or lower(f.nameEn) = lower(:nameEn))
            """)
    boolean existsOtherWithName(@Param("id") Long id, @Param("nameSr") String nameSr, @Param("nameEn") String nameEn);

    @Query(value = "select exists (select 1 from listing_feature where feature_id = :id)", nativeQuery = true)
    boolean isInUse(@Param("id") Long id);
}
