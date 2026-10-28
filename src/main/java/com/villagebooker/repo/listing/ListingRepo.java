package com.villagebooker.repo.listing;

import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.model.Listing;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

// JpaSpecificationExecutor: public search with optional filters (district, type, guests, price, dates)
public interface ListingRepo extends JpaRepository<Listing, Long>, JpaSpecificationExecutor<Listing> {

    // serializes booking creation and unavailable-period changes for one listing (spec 5.0)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Listing l where l.id = :id")
    Optional<Listing> findByIdForUpdate(@Param("id") Long id);

    Page<Listing> findByStatus(ListingStatus status, Pageable pageable);

    Page<Listing> findByOwnerIdAndStatusNot(Long ownerId, ListingStatus status, Pageable pageable);
}
