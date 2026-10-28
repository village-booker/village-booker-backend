package com.villagebooker.controller.listing;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs against the configured database; @Transactional rolls every test back, nothing stays in the DB.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OwnerListingFlowTest {

    private static final String BASE = "/api/owner/listings";

    @Autowired
    private MockMvc mvc;

    @Test
    void ownerCreatesAccommodationWaitingForApproval() throws Exception {
        String token = ownerToken();

        create(token, "/accommodations", accommodationJson("FREE", 7, null, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.type").value("ACCOMMODATION"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.name").value("Brvnara na Tari"))
                .andExpect(jsonPath("$.district").value("Zlatiborski"))
                .andExpect(jsonPath("$.freeCancellationDays").value(7))
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andExpect(jsonPath("$.accommodation.basePricePerNight").value(5000.00))
                .andExpect(jsonPath("$.accommodation.baseGuests").value(2))
                .andExpect(jsonPath("$.tour").doesNotExist());
    }

    @Test
    void ownerCreatesTour() throws Exception {
        create(ownerToken(), "/tours", tourJson())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TOUR"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.tour.difficulty").value("MODERATE"))
                .andExpect(jsonPath("$.tour.pricePerPerson").value(3500.00))
                .andExpect(jsonPath("$.accommodation").doesNotExist());
    }

    @Test
    void guestWhoIsNotAHostCannotCreate() throws Exception {
        create(userToken(), "/accommodations", accommodationJson("FREE", 7, null, 2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void statusFromTheRequestIsIgnored() throws Exception {
        String json = accommodationJson("FREE", 7, null, 2).replace("{", "{\"status\": \"APPROVED\", ");

        create(ownerToken(), "/accommodations", json)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void missingRequiredFieldsReturnValidationErrors() throws Exception {
        create(ownerToken(), "/accommodations", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.name.code").value("NotBlank"))
                .andExpect(jsonPath("$.errors.basePricePerNight.code").value("NotNull"));
    }

    @Test
    void baseGuestsOutsideGuestRangeReturns400() throws Exception {
        // minGuests 1, maxGuests 4, baseGuests 6
        create(ownerToken(), "/accommodations", accommodationJson("FREE", 7, null, 6))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BASE_GUESTS_OUT_OF_RANGE"))
                .andExpect(jsonPath("$.params.maxGuests").value(4));
    }

    @Test
    void minGuestsAboveMaxGuestsReturns400() throws Exception {
        String json = tourJson().replace("\"minGuests\": 2", "\"minGuests\": 20");

        create(ownerToken(), "/tours", json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GUEST_RANGE_INVALID"));
    }

    @Test
    void cancellationTermsMustMatchThePolicy() throws Exception {
        String token = ownerToken();

        create(token, "/accommodations", accommodationJson("FREE", null, null, 2))
                .andExpect(jsonPath("$.code").value("FREE_CANCELLATION_DAYS_REQUIRED"));
        create(token, "/accommodations", accommodationJson("FREE", 7, "50", 2))
                .andExpect(jsonPath("$.code").value("REFUND_PERCENTAGE_NOT_ALLOWED"));
        create(token, "/accommodations", accommodationJson("PARTIAL", 7, null, 2))
                .andExpect(jsonPath("$.code").value("REFUND_PERCENTAGE_REQUIRED"));
        create(token, "/accommodations", accommodationJson("NON_REFUNDABLE", 7, null, 2))
                .andExpect(jsonPath("$.code").value("FREE_CANCELLATION_DAYS_NOT_ALLOWED"));

        create(token, "/accommodations", accommodationJson("PARTIAL", null, "50", 2))
                .andExpect(status().isCreated());
        create(token, "/accommodations", accommodationJson("NON_REFUNDABLE", null, null, 2))
                .andExpect(status().isCreated());
    }

    @Test
    void depositIsNotAllowedWhenPayingOnSite() throws Exception {
        String json = accommodationJson("FREE", 7, null, 2)
                .replace("\"paymentMethod\": \"COMBINED\"", "\"paymentMethod\": \"ON_SITE\"");

        create(ownerToken(), "/accommodations", json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEPOSIT_NOT_ALLOWED_FOR_ON_SITE"));
    }

    @Test
    void ownerSeesOnlyOwnListingsNewestFirst() throws Exception {
        String token = ownerToken();
        create(token, "/accommodations", accommodationJson("FREE", 7, null, 2)).andExpect(status().isCreated());
        Long tourId = idOf(create(token, "/tours", tourJson()));
        create(ownerToken(), "/tours", tourJson()).andExpect(status().isCreated());

        mvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(tourId))
                .andExpect(jsonPath("$.content[0].price").value(3500.00))
                .andExpect(jsonPath("$.content[1].type").value("ACCOMMODATION"))
                .andExpect(jsonPath("$.content[1].price").value(5000.00));

        mvc.perform(get(BASE).param("size", "1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void ownerReadsOwnListingButNotSomeoneElses() throws Exception {
        String token = ownerToken();
        Long id = idOf(create(token, "/tours", tourJson()));

        mvc.perform(get(BASE + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.tour.durationDays").value(2));

        mvc.perform(get(BASE + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_LISTING_OWNER"));

        mvc.perform(get(BASE + "/999999999").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
    }

    private ResultActions create(String token, String path, String json) throws Exception {
        return mvc.perform(post(BASE + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static Long idOf(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String userToken() throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "test-%s@example.com", "password": "secret-pass-123",
                                 "firstName": "Ana", "lastName": "Anić", "acceptTerms": true}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    // registers a new user and makes them a host; returns the OWNER token
    private String ownerToken() throws Exception {
        String body = mvc.perform(post("/api/users/me/become-host")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankAccount": "160-5100000123456-65", "acceptHostTerms": true}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    // minGuests 1, maxGuests 4, COMBINED payment with 20% deposit
    private static String accommodationJson(String policy, Integer freeDays, String refund, int baseGuests) {
        return """
                {"name": "Brvnara na Tari", "description": "Pogled na Drinu",
                 "minGuests": 1, "maxGuests": 4,
                 "place": "Mitrovac", "municipality": "Bajina Bašta", "district": "Zlatiborski",
                 "latitude": 43.927351, "longitude": 19.431528,
                 "cancellationPolicy": "%s", "paymentMethod": "COMBINED", "depositPercentage": 20,
                 "freeCancellationDays": %s, "refundPercentage": %s,
                 "basePricePerNight": 5000, "baseGuests": %d, "extraGuestPrice": 1000,
                 "doubleBedCount": 1, "singleBedCount": 2, "roomCount": 2}
                """.formatted(policy, freeDays, refund, baseGuests);
    }

    private static String tourJson() {
        return """
                {"name": "Rafting Drinom", "minGuests": 2, "maxGuests": 12,
                 "place": "Perućac", "municipality": "Bajina Bašta", "district": "Zlatiborski",
                 "latitude": 43.957300, "longitude": 19.430100,
                 "cancellationPolicy": "NON_REFUNDABLE", "paymentMethod": "ON_SITE",
                 "durationDays": 2, "difficulty": "MODERATE", "pricePerPerson": 3500}
                """;
    }
}
