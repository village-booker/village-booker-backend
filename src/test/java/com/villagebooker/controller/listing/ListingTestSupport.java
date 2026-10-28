package com.villagebooker.controller.listing;

import com.jayway.jsonpath.JsonPath;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.enums.user.Role;
import com.villagebooker.model.User;
import com.villagebooker.repo.user.UserRepo;
import com.villagebooker.security.JwtService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public record ListingTestSupport(MockMvc mvc, UserRepo userRepo, JwtService jwtService, TermsProperties terms) {

    public static final String OWNER = "/api/owner/listings";
    public static final String ADMIN = "/api/admin/listings";
    public static final String ADMIN_CHANGE_REQUESTS = "/api/admin/change-requests";
    public static final String ADMIN_IMAGES = "/api/admin/images";

    // registers a new user and makes them a host; returns the OWNER token
    public String ownerToken() throws Exception {
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
    public String adminToken() {
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

    public Long createTour(String ownerToken) throws Exception {
        String body = mvc.perform(post(OWNER + "/tours")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tourJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    public Long createAccommodation(String ownerToken) throws Exception {
        String body = mvc.perform(post(OWNER + "/accommodations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accommodationJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    public ResultActions approveListing(String token, Long id) throws Exception {
        return mvc.perform(post(ADMIN + "/" + id + "/approve").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    public ResultActions rejectListing(String token, Long id, String reason) throws Exception {
        return mvc.perform(post(ADMIN + "/" + id + "/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"%s\"}".formatted(reason)));
    }

    public static String tourJson() {
        return """
                {"name": "Rafting Drinom", "minGuests": 2, "maxGuests": 12,
                 "place": "Perućac", "municipality": "Bajina Bašta", "district": "Zlatiborski",
                 "latitude": 43.957300, "longitude": 19.430100,
                 "cancellationPolicy": "NON_REFUNDABLE", "paymentMethod": "ON_SITE",
                 "durationDays": 2, "difficulty": "MODERATE", "pricePerPerson": 3500}
                """;
    }

    public static String accommodationJson() {
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
