package com.villagebooker.controller.user;

import com.jayway.jsonpath.JsonPath;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.controller.listing.ListingTestSupport;
import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.repo.listing.ListingRepo;
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

import static com.villagebooker.controller.listing.ListingTestSupport.OWNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The admin blocks a user: from that moment the user can neither log in nor use a token issued earlier.
// Runs against the configured database; @Transactional rolls every test back.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BlockUserFlowTest {

    private static final String ADMIN_USERS = "/api/admin/users";
    // ListingTestSupport.userToken() registers every test user with this password
    private static final String PASSWORD = "secret-pass-123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private ListingRepo listingRepo;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TermsProperties terms;

    private ListingTestSupport support;
    private String admin;

    @BeforeEach
    void setUp() {
        support = new ListingTestSupport(mvc, userRepo, jwtService, terms);
        admin = support.adminToken();
    }

    @Test
    void blockedUserIsStoppedAtOnceAndCanComeBackWhenUnblocked() throws Exception {
        String user = support.userToken();
        Long id = idOf(user);
        String email = emailOf(user);
        me(user).andExpect(status().isOk());

        block(id, "  Spam messages  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedAt").exists())
                .andExpect(jsonPath("$.blockReason").value("Spam messages"));

        // the token issued before the block no longer works, not even on public routes
        me(user)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BLOCKED"));
        mvc.perform(get("/api/listings").header(HttpHeaders.AUTHORIZATION, "Bearer " + user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BLOCKED"));
        mvc.perform(get("/api/listings")).andExpect(status().isOk());
        login(email, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BLOCKED"));
        // a wrong password does not tell that the account is blocked
        login(email, "wrong-pass-123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(get(ADMIN_USERS).param("blocked", "true").param("q", email)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].blockedAt").exists());
        mvc.perform(get(ADMIN_USERS).param("blocked", "false").param("q", email)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(jsonPath("$.totalElements").value(0));

        unblock(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedAt").value(nullValue()))
                .andExpect(jsonPath("$.blockReason").value(nullValue()));
        me(user).andExpect(status().isOk());
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void blockedHostCannotManageListingsWhichStayAsTheyAre() throws Exception {
        String host = support.ownerToken();
        Long listingId = support.createAccommodation(host);
        support.approveListing(admin, listingId).andExpect(status().isOk());

        block(idOf(host), "Fake listings").andExpect(status().isOk());

        mvc.perform(get(OWNER).header(HttpHeaders.AUTHORIZATION, "Bearer " + host))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BLOCKED"));
        mvc.perform(post(OWNER + "/" + listingId + "/pause").header(HttpHeaders.AUTHORIZATION, "Bearer " + host))
                .andExpect(status().isForbidden());
        assertThat(listingRepo.findById(listingId).orElseThrow().getStatus()).isEqualTo(ListingStatus.APPROVED);
    }

    @Test
    void blockingRules() throws Exception {
        String user = support.userToken();
        Long id = idOf(user);

        block(id, " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").value(not(empty())));
        block(id, "Abuse").andExpect(status().isOk());
        block(id, "Again")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_ALREADY_BLOCKED"));
        unblock(id).andExpect(status().isOk());
        unblock(id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_NOT_BLOCKED"));

        block(idOf(support.adminToken()), "No")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CANNOT_BLOCK_ADMIN"));
        block(Long.MAX_VALUE, "Gone")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        mvc.perform(post(ADMIN_USERS + "/" + id + "/block")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + support.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Not an admin\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private ResultActions block(Long id, String reason) throws Exception {
        return mvc.perform(post(ADMIN_USERS + "/" + id + "/block")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"%s\"}".formatted(reason)));
    }

    private ResultActions unblock(Long id) throws Exception {
        return mvc.perform(post(ADMIN_USERS + "/" + id + "/unblock").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin));
    }

    private ResultActions me(String token) throws Exception {
        return mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    private Long idOf(String token) throws Exception {
        return ((Number) JsonPath.read(body(token), "$.id")).longValue();
    }

    private String emailOf(String token) throws Exception {
        return JsonPath.read(body(token), "$.email");
    }

    private String body(String token) throws Exception {
        return me(token).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
}
