package com.villagebooker.repo.listing;

import com.villagebooker.model.Accommodation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// JpaSpecificationExecutor: public search with a type, so it can sort by the subtype's price
public interface AccommodationRepo extends JpaRepository<Accommodation, Long>, JpaSpecificationExecutor<Accommodation> {
}
