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

// Booking requests for the tests. Every booking starts as a request the host approves. Listings come from
// ListingTestSupport: accommodationJson() is ON_SITE, NON_REFUNDABLE, 1..4 guests, 5000 per night for 2 guests
// (+1000 per extra guest); tourJson() is 2 days, 2..12 guests per date, 3500 per person.
public record BookingTestSupport(MockMvc mvc) {

    public static final String BOOKINGS = "/api/bookings";
    public static final String OWNER_BOOKINGS = "/api/owner/bookings";

    private static final String DEFAULT_TERMS = "\"cancellationPolicy\": \"NON_REFUNDABLE\", \"paymentMethod\": \"ON_SITE\"";

    // 30 % deposit online after the approval, free cancellation until 7 days before arrival
    public static String depositJson() {
        return ListingTestSupport.accommodationJson().replace(DEFAULT_TERMS,
                "\"cancellationPolicy\": \"FREE\", \"freeCancellationDays\": 7, "
                        + "\"paymentMethod\": \"ONLINE\", \"depositPercentage\": 30");
    }

    // accommodationJson() / tourJson() with other cancellation and payment terms, e.g.
    // "\"cancellationPolicy\": \"PARTIAL\", \"refundPercentage\": 40, \"paymentMethod\": \"ON_SITE\""
    public static String withTerms(String listingJson, String terms) {
        return listingJson.replace(DEFAULT_TERMS, terms);
    }

    // simulated deposit payment (app.payments.simulated=true)
    public ResultActions pay(String token, Long bookingId) throws Exception {
        return mvc.perform(post(BOOKINGS + "/" + bookingId + "/pay").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
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

    // booked and approved by the host: CONFIRMED, or AWAITING_PAYMENT with a deposit
    public Long bookApproved(String token, String ownerToken, Long listingId, LocalDate arrival, LocalDate departure,
                             int guests) throws Exception {
        Long id = book(token, listingId, arrival, departure, guests);
        approve(ownerToken, id).andExpect(status().isOk());
        return id;
    }

    public ResultActions approve(String ownerToken, Long bookingId) throws Exception {
        return mvc.perform(post(OWNER_BOOKINGS + "/" + bookingId + "/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken));
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
