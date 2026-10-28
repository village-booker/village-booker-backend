package com.villagebooker.service.listing;

import com.villagebooker.common.exception.BadRequestException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.common.time.AppTime;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.feature.FeatureResponse;
import com.villagebooker.dto.listing.AvailabilityResponse;
import com.villagebooker.dto.listing.DateRangeResponse;
import com.villagebooker.dto.listing.ListingCardResponse;
import com.villagebooker.dto.listing.ListingImageResponse;
import com.villagebooker.dto.listing.ListingResponse;
import com.villagebooker.dto.listing.ListingSearchRequest;
import com.villagebooker.dto.listing.PublicListingResponse;
import com.villagebooker.dto.tour.TourDateResponse;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.enums.listing.ImageStatus;
import com.villagebooker.enums.listing.ListingSort;
import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.enums.listing.ListingType;
import com.villagebooker.enums.tour.TourDateStatus;
import com.villagebooker.model.Accommodation;
import com.villagebooker.model.Listing;
import com.villagebooker.model.Tour;
import com.villagebooker.model.User;
import com.villagebooker.repo.booking.BookingRepo;
import com.villagebooker.repo.listing.AccommodationRepo;
import com.villagebooker.repo.listing.ListingRepo;
import com.villagebooker.repo.listing.TourRepo;
import com.villagebooker.repo.listing.UnavailablePeriodRepo;
import com.villagebooker.repo.tour.TourDateRepo;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

// What guests see: search, listing page, accommodation calendar. No login needed (SecurityConfig).
// Search shows only APPROVED listings; a listing page is also open for PAUSED_BY_OWNER (guests who already booked it).
@Service
@RequiredArgsConstructor
public class PublicListingService {

    static final int MAX_SEARCH_DAYS = 90;
    static final int MAX_CALENDAR_DAYS = 366;

    private static final Set<ListingStatus> VISIBLE = Set.of(ListingStatus.APPROVED, ListingStatus.PAUSED_BY_OWNER);
    private static final List<TourDateStatus> BOOKABLE_DATE = List.of(TourDateStatus.OPEN, TourDateStatus.CONFIRMED);

    private final ListingRepo listingRepo;
    private final AccommodationRepo accommodationRepo;
    private final TourRepo tourRepo;
    private final TourDateRepo tourDateRepo;
    private final BookingRepo bookingRepo;
    private final UnavailablePeriodRepo unavailablePeriodRepo;
    private final MainImages mainImages;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<ListingCardResponse> search(ListingSearchRequest request, Pageable page) {
        LocalDate today = AppTime.today(clock);
        checkSearch(request, today);
        ListingSort sort = request.sort() != null ? request.sort() : ListingSort.NEWEST;
        PageRequest pageable = PageRequest.of(page.getPageNumber(), page.getPageSize(), sortOf(sort, request.type()));

        // with a type the subtype repository is used, because only there the price can be sorted on
        Page<? extends Listing> result = switch (request.type()) {
            case null -> listingRepo.findAll(PublicListingService.<Listing>spec(request, today, sort), pageable);
            case ACCOMMODATION ->
                    accommodationRepo.findAll(PublicListingService.<Accommodation>spec(request, today, sort), pageable);
            case TOUR -> tourRepo.findAll(PublicListingService.<Tour>spec(request, today, sort), pageable);
        };
        Map<Long, String> images = mainImages.forGuests(result.getContent());
        return PageResponse.of(result, listing -> toCard(listing, request, images.get(listing.getId())));
    }

    @Transactional(readOnly = true)
    public PublicListingResponse get(Long listingId) {
        Listing listing = findVisible(listingId);
        ListingResponse full = ListingResponse.from(listing);
        User host = listing.getOwner();

        List<TourDateResponse> dates = listing instanceof Tour
                ? tourDateRepo.findByTourIdAndStatusInAndStartDateGreaterThanEqualOrderByStartDate(
                                listingId, BOOKABLE_DATE, AppTime.today(clock).plusDays(1)).stream()
                        .map(TourDateResponse::from)
                        .filter(d -> d.freePlaces() > 0)
                        .toList()
                : List.of();

        return new PublicListingResponse(
                full.id(), full.type(), listing.getStatus() == ListingStatus.APPROVED, full.name(), full.description(),
                full.requiresOwnerApproval(), full.minGuests(), full.maxGuests(), full.address(), full.place(),
                full.municipality(), full.district(), full.latitude(), full.longitude(), full.cancellationPolicy(),
                full.paymentMethod(), full.depositPercentage(), full.freeCancellationDays(), full.refundPercentage(),
                full.rating(), full.reviewCount(), full.accommodation(), full.tour(),
                full.images().stream().filter(i -> i.status() == ImageStatus.APPROVED).toList(),
                full.features(),
                new PublicListingResponse.Host(host.getId(), host.getFirstName(), host.getImageUrl(), host.getCreatedAt()),
                dates);
    }

    // taken nights between from and to (default: today .. today + 1 year), bookings and blocks merged
    @Transactional(readOnly = true)
    public AvailabilityResponse availability(Long listingId, LocalDate from, LocalDate to) {
        if (!(findVisible(listingId) instanceof Accommodation)) {
            throw OwnerListingService.typeMismatch(listingId);
        }
        LocalDate today = AppTime.today(clock);
        LocalDate start = from != null ? from : today;
        LocalDate end = to != null ? to : start.plusDays(MAX_CALENDAR_DAYS - 1);
        if (end.isBefore(start) || start.plusDays(MAX_CALENDAR_DAYS).isBefore(end)) {
            throw new BadRequestException(ErrorCode.DATE_RANGE_INVALID,
                    "to must not be before from, and the range can have at most " + MAX_CALENDAR_DAYS + " days.",
                    Map.of("maxDays", MAX_CALENDAR_DAYS));
        }

        List<DateRangeResponse> taken = Stream.concat(
                        bookingRepo.findOverlapping(listingId, start, end, BookingStatus.ACTIVE).stream()
                                .map(b -> new DateRangeResponse(b.getStartDate(), b.getEndDate())),
                        unavailablePeriodRepo.findOverlapping(listingId, start, end).stream()
                                .map(p -> new DateRangeResponse(p.getStartDate(), p.getEndDate())))
                .toList();
        return new AvailabilityResponse(start, end, merge(taken));
    }

    private Listing findVisible(Long listingId) {
        return listingRepo.findById(listingId)
                .filter(l -> VISIBLE.contains(l.getStatus()))
                .map(l -> (Listing) Hibernate.unproxy(l))
                .orElseThrow(() -> OwnerListingService.listingNotFound(listingId));
    }

    private static void checkSearch(ListingSearchRequest r, LocalDate today) {
        if ((r.dateFrom() == null) != (r.dateTo() == null)) {
            throw datesInvalid("dateFrom and dateTo go together.");
        }
        if (r.dateFrom() != null) {
            if (r.dateFrom().isBefore(today) || !r.dateTo().isAfter(r.dateFrom())) {
                throw datesInvalid("dateFrom must be today or later and dateTo after dateFrom.");
            }
            if (r.dateFrom().plusDays(MAX_SEARCH_DAYS).isBefore(r.dateTo())) {
                throw datesInvalid("The searched period can have at most " + MAX_SEARCH_DAYS + " days.");
            }
        }
        boolean priceUsed = r.minPrice() != null || r.maxPrice() != null
                || r.sort() == ListingSort.PRICE_ASC || r.sort() == ListingSort.PRICE_DESC;
        if (priceUsed && r.type() == null) {
            throw new BadRequestException(ErrorCode.PRICE_FILTER_NEEDS_TYPE,
                    "Price filters and price sorting need a listing type.");
        }
        if (r.minPrice() != null && r.maxPrice() != null && r.minPrice().compareTo(r.maxPrice()) > 0) {
            throw new BadRequestException(ErrorCode.PRICE_RANGE_INVALID, "minPrice must not be greater than maxPrice.");
        }
    }

    private static BadRequestException datesInvalid(String message) {
        return new BadRequestException(ErrorCode.DATE_RANGE_INVALID, message, Map.of("maxDays", MAX_SEARCH_DAYS));
    }

    private static <T extends Listing> Specification<T> spec(ListingSearchRequest request, LocalDate today,
                                                             ListingSort sort) {
        Specification<T> search = ListingSearchSpecs.search(request, today);
        return sort == ListingSort.RATING ? search.and(ListingSearchSpecs.orderByRating()) : search;
    }

    private static Sort sortOf(ListingSort sort, ListingType type) {
        Sort newest = Sort.by(Sort.Direction.DESC, "id");
        String price = type == ListingType.TOUR ? "pricePerPerson" : "basePricePerNight";
        return switch (sort) {
            case NEWEST -> newest;
            case RATING -> Sort.unsorted();
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, price).and(newest);
            case PRICE_DESC -> Sort.by(Sort.Direction.DESC, price).and(newest);
        };
    }

    private static ListingCardResponse toCard(Listing listing, ListingSearchRequest r, String mainImageUrl) {
        ListingType type;
        BigDecimal price;
        BigDecimal total = null;
        switch (listing) {
            case Accommodation a -> {
                type = ListingType.ACCOMMODATION;
                price = a.getBasePricePerNight();
                if (r.dateFrom() != null) {
                    int guests = r.guests() != null ? r.guests() : a.getMinGuests();
                    total = ListingPrices.accommodationTotal(a, guests, r.dateFrom(), r.dateTo());
                }
            }
            case Tour t -> {
                type = ListingType.TOUR;
                price = t.getPricePerPerson();
                if (r.guests() != null) {
                    total = ListingPrices.tourTotal(t, r.guests());
                }
            }
            default -> throw new IllegalStateException("Unknown listing type: " + listing.getClass());
        }
        return new ListingCardResponse(listing.getId(), type, listing.getName(), listing.getPlace(),
                listing.getMunicipality(), listing.getDistrict(), listing.getMinGuests(), listing.getMaxGuests(),
                price, total, listing.getRating(), listing.getReviewCount(), mainImageUrl);
    }

    // overlapping or touching closed intervals become one
    private static List<DateRangeResponse> merge(List<DateRangeResponse> ranges) {
        List<DateRangeResponse> sorted = ranges.stream().sorted(Comparator.comparing(DateRangeResponse::startDate)).toList();
        List<DateRangeResponse> merged = new ArrayList<>();
        for (DateRangeResponse r : sorted) {
            if (!merged.isEmpty() && !r.startDate().isAfter(merged.getLast().endDate().plusDays(1))) {
                DateRangeResponse last = merged.removeLast();
                LocalDate end = r.endDate().isAfter(last.endDate()) ? r.endDate() : last.endDate();
                merged.add(new DateRangeResponse(last.startDate(), end));
            } else {
                merged.add(r);
            }
        }
        return merged;
    }
}
