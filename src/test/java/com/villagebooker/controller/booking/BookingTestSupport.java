package com.villagebooker.controller.booking;

import com.jayway.jsonpath.JsonPath;
import com.villagebooker.controller.listing.ListingTestSupport;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static com.villagebooker.controller.listing.ListingTestSupport.OWNER;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Booking requests for the tests. Listings come from ListingTestSupport: accommodationJson() is instant, ON_SITE,
// NON_REFUNDABLE, 1..4 guests, 5000 per night for 2 guests (+1000 per extra guest); tourJson() is 2 days,
// 2..12 guests per date, 3500 per person.
public record BookingTestSupport(MockMvc mvc) {

    public static final String BOOKINGS = "/api/bookings";
    public static final String OWNER_BOOKINGS = "/api/owner/bookings";

    private static final String DEFAULT_TERMS = "\"cancellationPolicy\": \"NON_REFUNDABLE\", \"paymentMethod\": \"ON_SITE\"";

    // the host has to approve every booking; no deposit
    public static String manualApprovalJson() {
        return ListingTestSupport.accommodationJson()
                .replace("\"minGuests\"", "\"requiresOwnerApproval\": true, \"minGuests\"");
    }

    // instant, 30 % deposit online, free cancellation until 7 days before arrival
    public static String depositJson() {
        return ListingTestSupport.accommodationJson().replace(DEFAULT_TERMS,
                "\"cancellationPolicy\": \"FREE\", \"freeCancellationDays\": 7, "
                        + "\"paymentMethod\": \"ONLINE\", \"depositPercentage\": 30");
    }

    // host approval first, then the 30 % deposit
    public static String manualApprovalWithDepositJson() {
        return depositJson().replace("\"minGuests\"", "\"requiresOwnerApproval\": true, \"minGuests\"");
    }

    public ResultActions bookAccommodation(String token, Long listingId, LocalDate arrival, LocalDate departure,
                                           int guests) throws Exception {
        return mvc.perform(post(BOOKINGS + "/accommodations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"listingId\": %d, \"arrival\": \"%s\", \"departure\": \"%s\", \"guestCount\": %d}"
                        .formatted(listingId, arrival, departure, guests)));
    }

    public ResultActions bookTour(String token, Long dateId, int guests) throws Exception {
        return mvc.perform(post(BOOKINGS + "/tours")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tourDateId\": %d, \"guestCount\": %d}".formatted(dateId, guests)));
    }

    // created booking -> its id
    public Long book(String token, Long listingId, LocalDate arrival, LocalDate departure, int guests)
            throws Exception {
        return idOf(bookAccommodation(token, listingId, arrival, departure, guests).andExpect(status().isCreated()));
    }

    public Long createDate(String ownerToken, Long tourId, LocalDate start) throws Exception {
        return idOf(mvc.perform(post(OWNER + "/tours/" + tourId + "/dates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\": \"%s\", \"minGuestsDeadlineDaysBefore\": 2}".formatted(start)))
                .andExpect(status().isCreated()));
    }

    public static Long idOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
