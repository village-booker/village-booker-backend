package com.villagebooker.controller;

import com.jayway.jsonpath.JsonPath;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.enums.Role;
import com.villagebooker.model.User;
import com.villagebooker.repo.UserRepo;
import com.villagebooker.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs against the configured database; @Transactional rolls every test back, nothing stays in the DB.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BecomeHostFlowTest {

    private static final String VALID_ACCOUNT = "160-5100000123456-65";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TermsProperties terms;

    @Test
    void userBecomesHostAndGetsANewOwnerToken() throws Exception {
        String email = uniqueEmail();
        String oldToken = register(email);

        String body = becomeHost(oldToken, "205-9011003562-09", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("OWNER"))
                .andExpect(jsonPath("$.user.host").value(true))
                .andReturn().getResponse().getContentAsString();
        String newToken = JsonPath.read(body, "$.accessToken");

        User user = userRepo.findByEmail(email).orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.OWNER);
        assertThat(user.getBankAccount()).isEqualTo("205-0009011003562-09");
        assertThat(user.getHostTermsVersion()).isEqualTo(terms.hostVersion());
        assertThat(user.getHostTermsAcceptedAt()).isNotNull();

        // the new token carries OWNER, the old one still says USER until it expires
        probeOwner(newToken).andExpect(status().isOk());
        probeOwner(oldToken).andExpect(status().isForbidden());
    }

    @Test
    void becomingHostTwiceReturns409() throws Exception {
        String token = register(uniqueEmail());
        becomeHost(token, VALID_ACCOUNT, true).andExpect(status().isOk());

        // the old (USER) token is used on purpose: the service checks the role in the database
        becomeHost(token, VALID_ACCOUNT, true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_ALREADY_HOST"));
    }

    @Test
    void withoutAcceptingHostTermsReturns400() throws Exception {
        String token = register(uniqueEmail());

        becomeHost(token, VALID_ACCOUNT, false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.acceptHostTerms").exists());
    }

    @Test
    void invalidBankAccountReturns400AndRoleStaysUser() throws Exception {
        String email = uniqueEmail();
        String token = register(email);

        becomeHost(token, "160-5100000123456-66", true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BANK_ACCOUNT"));

        assertThat(userRepo.findByEmail(email).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void adminCannotBecomeHost() throws Exception {
        User admin = new User();
        admin.setEmail(uniqueEmail());
        admin.setPasswordHash("not-used");
        admin.setFirstName("Admin");
        admin.setLastName("Adminić");
        admin.setRole(Role.ADMIN);
        admin.setTermsVersion(terms.version());
        admin.setTermsAcceptedAt(Instant.now());
        userRepo.saveAndFlush(admin);
        String token = jwtService.issue(admin).token();

        becomeHost(token, VALID_ACCOUNT, true)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_CANNOT_BE_HOST"));
    }

    @Test
    void withoutTokenReturns401() throws Exception {
        mvc.perform(post("/api/users/me/become-host")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(becomeHostJson(VALID_ACCOUNT, true)))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions becomeHost(String token, String bankAccount, boolean acceptHostTerms) throws Exception {
        return mvc.perform(post("/api/users/me/become-host")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(becomeHostJson(bankAccount, acceptHostTerms)));
    }

    private ResultActions probeOwner(String token) throws Exception {
        return mvc.perform(get("/api/probe/owner").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String register(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "secret-pass-123", "firstName": "Ana", "lastName": "Anić", "acceptTerms": true}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String becomeHostJson(String bankAccount, boolean acceptHostTerms) {
        return """
                {"bankAccount": "%s", "acceptHostTerms": %s}
                """.formatted(bankAccount, acceptHostTerms);
    }

    private static String uniqueEmail() {
        return "test-" + UUID.randomUUID() + "@example.com";
    }
}
