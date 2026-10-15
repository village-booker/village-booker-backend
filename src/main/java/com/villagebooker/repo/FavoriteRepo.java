package com.villagebooker.repo;

import com.villagebooker.model.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRepo extends JpaRepository<Favorite, Long> {

    @EntityGraph(attributePaths = "listing")
    Page<Favorite> findByUserId(Long userId, Pageable pageable);
    boolean existsByUserIdAndListingId(Long userId, Long listingId);
    long deleteByUserIdAndListingId(Long userId, Long listingId);
}
