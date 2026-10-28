package com.villagebooker.repo.listing;

import com.villagebooker.model.Tour;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourRepo extends JpaRepository<Tour, Long> {
}
