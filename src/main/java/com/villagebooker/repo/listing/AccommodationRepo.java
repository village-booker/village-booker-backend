package com.villagebooker.repo.listing;

import com.villagebooker.model.Accommodation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccommodationRepo extends JpaRepository<Accommodation, Long> {
}
