package com.villagebooker.service.listing;

import com.villagebooker.dto.listing.ListingSearchRequest;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.enums.listing.ListingType;
import com.villagebooker.enums.tour.TourDateStatus;
import com.villagebooker.model.Accommodation;
import com.villagebooker.model.Booking;
import com.villagebooker.model.Listing;
import com.villagebooker.model.Tour;
import com.villagebooker.model.TourDate;
import com.villagebooker.model.UnavailablePeriod;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Public search as a JPA Specification. Subtype conditions (price, dates, guests per type) are subqueries on
// Accommodation / Tour, so the same specification works for Listing (no type) and for one subtype (sort by price).
// Closed intervals overlap when a.start <= b.end and b.start <= a.end (spec 1.2).
final class ListingSearchSpecs {

    private static final List<TourDateStatus> BOOKABLE_DATE = List.of(TourDateStatus.OPEN, TourDateStatus.CONFIRMED);

    private ListingSearchSpecs() {
    }

    static <T extends Listing> Specification<T> search(ListingSearchRequest r, LocalDate today) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("status"), ListingStatus.APPROVED));

            if (hasText(r.q())) {
                String like = "%" + escapeLike(r.q().trim().toLowerCase(Locale.ROOT)) + "%";
                where.add(cb.or(
                        cb.like(cb.lower(root.<String>get("name")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("place")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("municipality")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("district")), like, '\\')));
            }
            if (hasText(r.district())) {
                where.add(cb.equal(cb.lower(root.<String>get("district")), r.district().trim().toLowerCase(Locale.ROOT)));
            }
            if (r.guests() != null) {
                where.add(cb.ge(root.<Integer>get("maxGuests"), r.guests()));
            }
            if (r.featureIds() != null) {
                for (Long featureId : r.featureIds()) {
                    where.add(cb.exists(hasFeature(root, query, cb, featureId)));
                }
            }

            List<Predicate> byType = new ArrayList<>();
            if (r.type() == null || r.type() == ListingType.ACCOMMODATION) {
                byType.add(root.get("id").in(availableAccommodations(r, query, cb)));
            }
            if (r.type() == null || r.type() == ListingType.TOUR) {
                byType.add(root.get("id").in(bookableTours(r, today, query, cb)));
            }
            where.add(cb.or(byType.toArray(Predicate[]::new)));
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    static <T extends Listing> Specification<T> orderByRating() {
        return (root, query, cb) -> {
            if (!Long.class.equals(query.getResultType())) {
                query.orderBy(
                        cb.desc(cb.coalesce(root.<BigDecimal>get("rating"), BigDecimal.ZERO)),
                        cb.desc(root.get("reviewCount")),
                        cb.desc(root.get("id")));
            }
            return null;
        };
    }

    private static Subquery<Long> hasFeature(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder cb, Long featureId) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<Listing> listing = sq.from(Listing.class);
        Join<Object, Object> feature = listing.join("features");
        return sq.select(listing.<Long>get("id"))
                .where(cb.equal(listing.get("id"), root.get("id")), cb.equal(feature.get("id"), featureId));
    }

    private static Subquery<Long> availableAccommodations(ListingSearchRequest r, CriteriaQuery<?> query,
                                                          CriteriaBuilder cb) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<Accommodation> a = sq.from(Accommodation.class);
        List<Predicate> where = new ArrayList<>();
        if (r.guests() != null) {
            where.add(cb.le(a.<Integer>get("minGuests"), r.guests()));
        }
        if (r.minPrice() != null) {
            where.add(cb.ge(a.<BigDecimal>get("basePricePerNight"), r.minPrice()));
        }
        if (r.maxPrice() != null) {
            where.add(cb.le(a.<BigDecimal>get("basePricePerNight"), r.maxPrice()));
        }
        if (r.dateFrom() != null) {
            LocalDate firstNight = r.dateFrom();
            LocalDate lastNight = r.dateTo().minusDays(1);

            Subquery<Long> booked = query.subquery(Long.class);
            Root<Booking> b = booked.from(Booking.class);
            booked.select(b.<Long>get("id")).where(
                    cb.equal(b.get("listing").get("id"), a.get("id")),
                    cb.isNull(b.get("tourDate")),
                    b.get("status").in(BookingStatus.ACTIVE),
                    cb.lessThanOrEqualTo(b.<LocalDate>get("startDate"), lastNight),
                    cb.greaterThanOrEqualTo(b.<LocalDate>get("endDate"), firstNight));
            where.add(cb.not(cb.exists(booked)));

            Subquery<Long> blocked = query.subquery(Long.class);
            Root<UnavailablePeriod> p = blocked.from(UnavailablePeriod.class);
            blocked.select(p.<Long>get("id")).where(
                    cb.equal(p.get("listing").get("id"), a.get("id")),
                    cb.lessThanOrEqualTo(p.<LocalDate>get("startDate"), lastNight),
                    cb.greaterThanOrEqualTo(p.<LocalDate>get("endDate"), firstNight));
            where.add(cb.not(cb.exists(blocked)));
        }
        return sq.select(a.<Long>get("id")).where(where.toArray(Predicate[]::new));
    }

    // a tour is shown only with at least one upcoming date that still has room (for the searched dates / guests)
    private static Subquery<Long> bookableTours(ListingSearchRequest r, LocalDate today, CriteriaQuery<?> query,
                                                CriteriaBuilder cb) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<Tour> t = sq.from(Tour.class);
        List<Predicate> where = new ArrayList<>();
        if (r.minPrice() != null) {
            where.add(cb.ge(t.<BigDecimal>get("pricePerPerson"), r.minPrice()));
        }
        if (r.maxPrice() != null) {
            where.add(cb.le(t.<BigDecimal>get("pricePerPerson"), r.maxPrice()));
        }

        Subquery<Long> dates = query.subquery(Long.class);
        Root<TourDate> d = dates.from(TourDate.class);
        List<Predicate> dateWhere = new ArrayList<>();
        dateWhere.add(cb.equal(d.get("tour").get("id"), t.get("id")));
        dateWhere.add(d.get("status").in(BOOKABLE_DATE));
        dateWhere.add(cb.greaterThan(d.<LocalDate>get("startDate"), today));
        int places = r.guests() != null ? r.guests() : 1;
        dateWhere.add(cb.ge(cb.diff(t.<Integer>get("maxGuests"), d.<Integer>get("bookedCount")), places));
        if (r.dateFrom() != null) {
            dateWhere.add(cb.between(d.<LocalDate>get("startDate"), r.dateFrom(), r.dateTo()));
        }
        dates.select(d.<Long>get("id")).where(dateWhere.toArray(Predicate[]::new));
        where.add(cb.exists(dates));

        return sq.select(t.<Long>get("id")).where(where.toArray(Predicate[]::new));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
