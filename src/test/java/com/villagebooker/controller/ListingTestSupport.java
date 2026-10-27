package com.villagebooker.controller;

import com.jayway.jsonpath.JsonPath;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.enums.Role;
import com.villagebooker.model.User;
import com.villagebooker.repo.UserRepo;
import com.villagebooker.security.JwtService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Users, tokens and listing JSON for the listing flow tests
record ListingTestSupport(MockMvc mvc, UserRepo userRepo, JwtService jwtService, TermsProperties terms) {

    static final String OWNER = "/api/owner/listings";
    static final String ADMIN = "/api/admin/listings";
    static final String ADMIN_CHANGE_REQUESTS = "/api/admin/change-requests";

    // registers a new user and makes them a host; returns the OWNER token
    String ownerToken() throws Exception {
        String registered = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "test-%s@example.com", "password": "secret-pass-123",
                                 "firstName": "Ana", "lastName": "Anić", "acceptTerms": true}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String body = mvc.perform(post("/api/users/me/become-host")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + JsonPath.read(registered, "$.accessToken"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankAccount": "160-5100000123456-65", "acceptHostTerms": true}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    // admins are set by hand in the database, so the test does the same
    String adminToken() {
        User admin = new User();
        admin.setEmail("test-" + UUID.randomUUID() + "@example.com");
        admin.setPasswordHash("not-used");
        admin.setFirstName("Admin");
        admin.setLastName("Adminić");
        admin.setRole(Role.ADMIN);
        admin.setTermsVersion(terms.version());
        admin.setTermsAcceptedAt(Instant.now());
        userRepo.saveAndFlush(admin);
        return jwtService.issue(admin).token();
    }

    Long createTour(String ownerToken) throws Exception {
        String body = mvc.perform(post(OWNER + "/tours")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tourJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    ResultActions approveListing(String token, Long id) throws Exception {
        return mvc.perform(post(ADMIN + "/" + id + "/approve").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    ResultActions rejectListing(String token, Long id, String reason) throws Exception {
        return mvc.perform(post(ADMIN + "/" + id + "/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"%s\"}".formatted(reason)));
    }

    static String tourJson() {
        return """
                {"name": "Rafting Drinom", "minGuests": 2, "maxGuests": 12,
                 "place": "Perućac", "municipality": "Bajina Bašta", "district": "Zlatiborski",
                 "latitude": 43.957300, "longitude": 19.430100,
                 "cancellationPolicy": "NON_REFUNDABLE", "paymentMethod": "ON_SITE",
                 "durationDays": 2, "difficulty": "MODERATE", "pricePerPerson": 3500}
                """;
    }

    static String accommodationJson() {
        return """
                {"name": "Brvnara na Tari", "minGuests": 1, "maxGuests": 4,
                 "place": "Mitrovac", "municipality": "Bajina Bašta", "district": "Zlatiborski",
                 "latitude": 43.927351, "longitude": 19.431528,
                 "cancellationPolicy": "NON_REFUNDABLE", "paymentMethod": "ON_SITE",
                 "basePricePerNight": 5000, "baseGuests": 2, "extraGuestPrice": 1000,
                 "doubleBedCount": 1, "singleBedCount": 2, "roomCount": 2}
                """;
    }
}
