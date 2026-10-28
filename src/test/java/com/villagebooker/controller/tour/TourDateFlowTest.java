package com.villagebooker.controller.tour;

import com.villagebooker.controller.listing.ListingTestSupport;

import com.jayway.jsonpath.JsonPath;
import com.villagebooker.common.time.AppTime;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.repo.tour.TourDateRepo;
import com.villagebooker.repo.user.UserRepo;
import com.villagebooker.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

import static com.villagebooker.controller.listing.ListingTestSupport.OWNER;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Dates of a tour (tourJson(): 2 days, 2..12 guests).
// Runs against the configured database; @Transactional rolls every test back, nothing stays in the DB.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TourDateFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private TourDateRepo tourDateRepo;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TermsProperties terms;

    @Autowired
    private Clock clock;

    private ListingTestSupport support;

    @BeforeEach
    void setUp() {
        support = new ListingTestSupport(mvc, userRepo, jwtService, terms);
    }

    @Test
    void ownerCreatesDateWithComputedEndAndDeadline() throws Exception {
        String owner = support.ownerToken();
        Long tourId = support.createTour(owner);
        LocalDate start = today().plusDays(20);

        createDate(owner, tourId, start, 3)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startDate").value(start.toString()))
                .andExpect(jsonPath("$.endDate").value(start.plusDays(1).toString()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.bookedCount").value(0))
                .andExpect(jsonPath("$.freePlaces").value(12))
                .andExpect(jsonPath("$.minGuestsDeadline").value(AppTime.startOfDay(start.minusDays(3)).toString()));

        mvc.perform(get(dates(tourId)).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void sameStartDateTwiceReturns409() throws Exception {
        String owner = support.ownerToken();
        Long tourId = support.createTour(owner);
        LocalDate start = today().plusDays(20);
        createDate(owner, tourId, start, 2).andExpect(status().isCreated());

        createDate(owner, tourId, start, 5)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOUR_DATE_ALREADY_EXISTS"));
    }

    @Test
    void dateMustBeInTheFutureWithADeadlineStillAhead() throws Exception {
        String owner = support.ownerToken();
        Long tourId = support.createTour(owner);

        createDate(owner, tourId, today(), 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOUR_DATE_IN_PAST"));

        // starts tomorrow, but the minimum had to be reached 2 days before
        createDate(owner, tourId, today().plusDays(1), 2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOUR_DATE_DEADLINE_PASSED"));
    }

    @Test
    void datesOnlyForOwnTours() throws Exception {
        String owner = support.ownerToken();
        Long accommodationId = support.createAccommodation(owner);
        Long tourId = support.createTour(owner);

        createDate(owner, accommodationId, today().plusDays(20), 2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LISTING_TYPE_MISMATCH"));
        createDate(support.ownerToken(), tourId, today().plusDays(20), 2)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_LISTING_OWNER"));
    }

    @Test
    void dateWithoutGuestsIsDeletedButOneWithGuestsIsNot() throws Exception {
        String owner = support.ownerToken();
        Long tourId = support.createTour(owner);
        Long empty = idOf(createDate(owner, tourId, today().plusDays(20), 2));
        Long withGuests = idOf(createDate(owner, tourId, today().plusDays(30), 2));
        tourDateRepo.findById(withGuests).orElseThrow().setBookedCount(3);

        deleteDate(owner, tourId, empty).andExpect(status().isNoContent());
        deleteDate(owner, tourId, withGuests)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOUR_DATE_HAS_BOOKINGS"))
                .andExpect(jsonPath("$.params.bookedCount").value(3));

        mvc.perform(get(dates(tourId)).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(withGuests));

        deleteDate(owner, tourId, 999_999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOUR_DATE_NOT_FOUND"));
    }

    private LocalDate today() {
        return AppTime.today(clock);
    }

    private static String dates(Long tourId) {
        return OWNER + "/tours/" + tourId + "/dates";
    }

    private ResultActions createDate(String token, Long tourId, LocalDate start, int daysBefore) throws Exception {
        return mvc.perform(post(dates(tourId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\": \"%s\", \"minGuestsDeadlineDaysBefore\": %d}".formatted(start, daysBefore)));
    }

    private ResultActions deleteDate(String token, Long tourId, Long dateId) throws Exception {
        return mvc.perform(delete(dates(tourId) + "/" + dateId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static Long idOf(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
