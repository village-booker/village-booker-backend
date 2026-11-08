package com.villagebooker.controller.user;

import com.jayway.jsonpath.JsonPath;
import com.villagebooker.common.time.AppTime;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.controller.booking.BookingTestSupport;
import com.villagebooker.controller.listing.ListingTestSupport;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The admin's user list and user details. Runs against the configured database, which may hold other users,
// so the searches use the random part of the test emails; @Transactional rolls every test back.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminUserFlowTest {

    private static final String ADMIN_USERS = "/api/admin/users";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TermsProperties terms;

    @Autowired
    private Clock clock;

    private ListingTestSupport support;
    private BookingTestSupport bookings;
    private String admin;

    @BeforeEach
    void setUp() {
        support = new ListingTestSupport(mvc, userRepo, jwtService, terms);
        bookings = new BookingTestSupport(mvc);
        admin = support.adminToken();
    }

    @Test
    void adminSearchesUsersByEmailOrNameAndRole() throws Exception {
        String guest = support.userToken();
        String host = support.ownerToken();
        String guestKey = emailKey(guest);
        String hostKey = emailKey(host);

        list("?q=" + guestKey.toUpperCase())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(idOf(guest)))
                .andExpect(jsonPath("$.content[0].role").value("USER"))
                .andExpect(jsonPath("$.content[0].firstName").value("Ana"))
                .andExpect(jsonPath("$.content[0].createdAt").exists());
        list("?q=" + guestKey + "&role=OWNER").andExpect(jsonPath("$.totalElements").value(0));
        list("?q=" + hostKey + "&role=OWNER")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(idOf(host)));
        // % and _ are plain characters, not wildcards
        list("?q=" + guestKey + "%25").andExpect(jsonPath("$.totalElements").value(0));
        list("?role=NOT_A_ROLE").andExpect(status().isBadRequest());
    }

    @Test
    void userDetailsHaveCountsButNoBankAccount() throws Exception {
        String host = support.ownerToken();
        String guest = support.userToken();
        Long cabin = support.createAccommodation(host);
        support.approveListing(admin, cabin).andExpect(status().isOk());
        support.createTour(host);
        LocalDate arrival = AppTime.today(clock).plusDays(10);
        Long booking = bookings.bookApproved(guest, host, cabin, arrival, arrival.plusDays(2), 2);
        bookings.book(guest, cabin, arrival.plusDays(5), arrival.plusDays(6), 2);
        mvc.perform(post(BookingTestSupport.OWNER_BOOKINGS + "/" + booking + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + host)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Flooded road\"}"))
                .andExpect(status().isOk());

        details(idOf(host))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.hostTermsVersion").value(terms.hostVersion()))
                .andExpect(jsonPath("$.listingCount").value(2))
                .andExpect(jsonPath("$.bookingCount").value(0))
                .andExpect(jsonPath("$.ownerCancellationCount").value(1))
                .andExpect(jsonPath("$.bankAccount").doesNotExist());
        details(idOf(guest))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.listingCount").value(0))
                .andExpect(jsonPath("$.bookingCount").value(2))
                .andExpect(jsonPath("$.ownerCancellationCount").value(0));
        details(Long.MAX_VALUE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void onlyAdminsSeeUsers() throws Exception {
        String host = support.ownerToken();
        mvc.perform(get(ADMIN_USERS).header(HttpHeaders.AUTHORIZATION, "Bearer " + host))
                .andExpect(status().isForbidden());
        mvc.perform(get(ADMIN_USERS + "/" + idOf(host)).header(HttpHeaders.AUTHORIZATION, "Bearer " + host))
                .andExpect(status().isForbidden());
        mvc.perform(get(ADMIN_USERS)).andExpect(status().isUnauthorized());
    }

    private ResultActions list(String query) throws Exception {
        return mvc.perform(get(ADMIN_USERS + query).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin));
    }

    private ResultActions details(Long id) throws Exception {
        return mvc.perform(get(ADMIN_USERS + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin));
    }

    private String me(String token) throws Exception {
        return mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private Long idOf(String token) throws Exception {
        return ((Number) JsonPath.read(me(token), "$.id")).longValue();
    }

    // test-<uuid>@example.com -> <uuid>, unique to this user
    private String emailKey(String token) throws Exception {
        String email = JsonPath.read(me(token), "$.email");
        return email.substring("test-".length(), email.indexOf('@'));
    }
}
