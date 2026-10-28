package com.villagebooker.controller.listing;

import com.jayway.jsonpath.JsonPath;
import com.villagebooker.common.time.AppTime;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.model.UnavailablePeriod;
import com.villagebooker.repo.listing.ListingRepo;
import com.villagebooker.repo.listing.UnavailablePeriodRepo;
import com.villagebooker.repo.user.UserRepo;
import com.villagebooker.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import static com.villagebooker.controller.listing.ListingTestSupport.OWNER;
import static com.villagebooker.controller.listing.ListingTestSupport.accommodationJson;
import static com.villagebooker.controller.listing.ListingTestSupport.tourJson;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The shared database may hold other listings, so every test names its listings uniquely and searches with q=<name>.
// accommodationJson(): 5000/night for 2 guests, +1000 per extra guest, 1..4 guests; tourJson(): 3500/person, 2..12.
// Runs against the configured database; @Transactional rolls every test back, nothing stays in the DB.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PublicListingFlowTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private ListingRepo listingRepo;

    @Autowired
    private UnavailablePeriodRepo unavailablePeriodRepo;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TermsProperties terms;

    @Autowired
    private Clock clock;

    private ListingTestSupport support;
    private String owner;
    private String admin;
    private String name;

    @BeforeEach
    void setUp() throws Exception {
        support = new ListingTestSupport(mvc, userRepo, jwtService, terms);
        owner = support.ownerToken();
        admin = support.adminToken();
        name = "Test " + UUID.randomUUID();
    }

    @Test
    void onlyApprovedListingsAreFoundWithoutLogin() throws Exception {
        Long pending = create("/accommodations", accommodation(name));
        Long approved = approved("/accommodations", accommodation(name));

        search("q", name)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(approved))
                .andExpect(jsonPath("$.content[0].type").value("ACCOMMODATION"))
                .andExpect(jsonPath("$.content[0].price").value(5000));

        mvc.perform(get("/api/listings/" + pending)).andExpect(status().isNotFound());
    }

    @Test
    void filtersByTypeAndGuests() throws Exception {
        approved("/accommodations", accommodation(name));
        Long tour = approved("/tours", tour(name));
        addTourDate(tour, today().plusDays(20));

        search("q", name, "type", "TOUR").andExpect(jsonPath("$.totalElements").value(1));
        // the accommodation takes at most 4 guests, the tour 12
        search("q", name, "guests", "5")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(tour));
        search("q", name, "guests", "13").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void accommodationWithTakenNightsIsNotFound() throws Exception {
        Long id = approved("/accommodations", accommodation(name));
        LocalDate d = today().plusDays(30);
        block(id, d, d.plusDays(2));

        // nights d-2, d-1: the departure day d is not a night, so it fits
        searchDates(d.minusDays(2), d).andExpect(jsonPath("$.totalElements").value(1));
        // night d+2 is blocked
        searchDates(d.plusDays(2), d.plusDays(4)).andExpect(jsonPath("$.totalElements").value(0));
        searchDates(d.plusDays(3), d.plusDays(5))
                .andExpect(jsonPath("$.totalElements").value(1))
                // 3 guests: (5000 + 1000) * 2 nights
                .andExpect(jsonPath("$.content[0].totalPrice").value(12000));
    }

    @Test
    void tourIsFoundOnlyWithAFreeDateInThePeriod() throws Exception {
        Long tour = approved("/tours", tour(name));
        search("q", name).andExpect(jsonPath("$.totalElements").value(0));

        LocalDate start = today().plusDays(20);
        addTourDate(tour, start);

        search("q", name, "dateFrom", start.minusDays(1).toString(), "dateTo", start.plusDays(1).toString())
                .andExpect(jsonPath("$.totalElements").value(1));
        search("q", name, "dateFrom", start.plusDays(5).toString(), "dateTo", start.plusDays(7).toString())
                .andExpect(jsonPath("$.totalElements").value(0));
        search("q", name, "guests", "3")
                .andExpect(jsonPath("$.content[0].totalPrice").value(10500));
    }

    @Test
    void priceFilterAndSortNeedAType() throws Exception {
        approved("/accommodations", accommodation(name));
        approved("/accommodations", accommodation(name).replace("\"basePricePerNight\": 5000", "\"basePricePerNight\": 3000"));

        search("q", name, "maxPrice", "4000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRICE_FILTER_NEEDS_TYPE"));

        search("q", name, "type", "ACCOMMODATION", "sort", "PRICE_ASC")
                .andExpect(jsonPath("$.content[0].price").value(3000))
                .andExpect(jsonPath("$.content[1].price").value(5000));
        search("q", name, "type", "ACCOMMODATION", "maxPrice", "4000")
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void ratingSortPutsListingsWithoutReviewsLast() throws Exception {
        Long unrated = approved("/accommodations", accommodation(name));
        Long rated = approved("/accommodations", accommodation(name));
        var listing = listingRepo.findById(rated).orElseThrow();
        listing.setRating(new java.math.BigDecimal("4.50"));
        listing.setReviewCount(2);
        listingRepo.flush();

        search("q", name, "sort", "RATING")
                .andExpect(jsonPath("$.content[0].id").value(rated))
                .andExpect(jsonPath("$.content[1].id").value(unrated));
    }

    @Test
    void invalidDatesReturn400() throws Exception {
        searchDates(today().minusDays(1), today().plusDays(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATE_RANGE_INVALID"));
        search("q", name, "dateFrom", today().plusDays(1).toString())
                .andExpect(jsonPath("$.code").value("DATE_RANGE_INVALID"));
    }

    @Test
    void cardShowsTheFirstApprovedImage() throws Exception {
        Long id = create("/accommodations", accommodation(name));
        upload(id).andExpect(jsonPath("$.status").value("APPROVED"));
        support.approveListing(admin, id).andExpect(status().isOk());
        // added to a public listing: PENDING, so it is neither the main image nor on the page
        upload(id).andExpect(jsonPath("$.status").value("PENDING"));

        search("q", name).andExpect(jsonPath("$.content[0].mainImageUrl").value(startsWith("https://fake.storage/")));
        mvc.perform(get("/api/listings/" + id))
                .andExpect(jsonPath("$.images.length()").value(1));
    }

    @Test
    void listingPageHidesModerationAndContactData() throws Exception {
        Long tour = approved("/tours", tour(name));
        addTourDate(tour, today().plusDays(20));

        mvc.perform(get("/api/listings/" + tour))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookable").value(true))
                .andExpect(jsonPath("$.host.firstName").value("Ana"))
                .andExpect(jsonPath("$.host.email").doesNotExist())
                .andExpect(jsonPath("$.rejectionReason").doesNotExist())
                .andExpect(jsonPath("$.tourDates.length()").value(1))
                .andExpect(jsonPath("$.tourDates[0].freePlaces").value(12));
    }

    @Test
    void calendarMergesBookingsAndBlockedNights() throws Exception {
        Long id = approved("/accommodations", accommodation(name));
        LocalDate d = today().plusDays(10);
        block(id, d, d.plusDays(1));
        block(id, d.plusDays(2), d.plusDays(3));
        block(id, d.plusDays(10), d.plusDays(10));

        mvc.perform(get("/api/listings/" + id + "/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.takenNights.length()").value(2))
                .andExpect(jsonPath("$.takenNights[0].startDate").value(d.toString()))
                .andExpect(jsonPath("$.takenNights[0].endDate").value(d.plusDays(3).toString()));

        Long tour = approved("/tours", tour(name));
        mvc.perform(get("/api/listings/" + tour + "/availability"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LISTING_TYPE_MISMATCH"));
    }

    private LocalDate today() {
        return AppTime.today(clock);
    }

    private static String accommodation(String name) {
        return accommodationJson().replace("\"name\": \"Brvnara na Tari\"", "\"name\": \"" + name + "\"");
    }

    private static String tour(String name) {
        return tourJson().replace("\"name\": \"Rafting Drinom\"", "\"name\": \"" + name + "\"");
    }

    private Long create(String path, String json) throws Exception {
        String body = mvc.perform(post(OWNER + path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private Long approved(String path, String json) throws Exception {
        Long id = create(path, json);
        support.approveListing(admin, id).andExpect(status().isOk());
        return id;
    }

    private void addTourDate(Long tourId, LocalDate start) throws Exception {
        mvc.perform(post(OWNER + "/tours/" + tourId + "/dates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\": \"%s\", \"minGuestsDeadlineDaysBefore\": 2}".formatted(start)))
                .andExpect(status().isCreated());
    }

    private void block(Long listingId, LocalDate start, LocalDate end) {
        UnavailablePeriod period = new UnavailablePeriod();
        period.setListing(listingRepo.getReferenceById(listingId));
        period.setStartDate(start);
        period.setEndDate(end);
        unavailablePeriodRepo.saveAndFlush(period);
    }

    private ResultActions upload(Long listingId) throws Exception {
        return mvc.perform(multipart(OWNER + "/" + listingId + "/images")
                .file(new MockMultipartFile("file", "image.png", "image/png", PNG))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner));
    }

    private ResultActions searchDates(LocalDate from, LocalDate to) throws Exception {
        return search("q", name, "dateFrom", from.toString(), "dateTo", to.toString(), "guests", "3");
    }

    private ResultActions search(String... params) throws Exception {
        var request = get("/api/listings");
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request);
    }
}
