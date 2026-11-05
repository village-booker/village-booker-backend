package com.villagebooker.repo.user;

import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.model.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface FavoriteRepo extends JpaRepository<Favorite, Long> {

    @EntityGraph(attributePaths = "listing")
    Page<Favorite> findByUserIdAndListingStatusIn(Long userId, Collection<ListingStatus> statuses, Pageable pageable);

    @Query("select f.listing.id from Favorite f where f.user.id = :userId and f.listing.id in :listingIds")
    List<Long> findListingIds(@Param("userId") Long userId, @Param("listingIds") Collection<Long> listingIds);

    boolean existsByUserIdAndListingId(Long userId, Long listingId);

    // idempotent even when two requests add the same listing at once (no unique violation, no aborted transaction)
    @Modifying
    @Query(value = """
            insert into favorite (user_id, listing_id, created_at) values (:userId, :listingId, :now)
            on conflict (user_id, listing_id) do nothing""", nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("listingId") Long listingId, @Param("now") Instant now);

    long deleteByUserIdAndListingId(Long userId, Long listingId);
}
