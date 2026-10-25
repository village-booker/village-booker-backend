package com.villagebooker.config;

import com.villagebooker.enums.Role;
import com.villagebooker.model.User;
import com.villagebooker.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Checks the real security chain with real tokens; no users are written to the database.
@SpringBootTest
@AutoConfigureMockMvc
class SecurityRulesTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private TermsProperties termsProperties;

    @Test
    void userCanUseUserEndpointsOnly() throws Exception {
        String token = tokenFor(Role.USER);

        call("/api/probe/user", token).andExpect(status().isOk());
        call("/api/probe/owner", token).andExpect(status().isForbidden());
        call("/api/probe/admin", token).andExpect(status().isForbidden());
    }

    @Test
    void ownerCanDoEverythingAUserCan() throws Exception {
        String token = tokenFor(Role.OWNER);

        call("/api/probe/user", token).andExpect(status().isOk());
        call("/api/probe/owner", token).andExpect(status().isOk());
        call("/api/probe/admin", token).andExpect(status().isForbidden());
    }

    @Test
    void adminIsNotAHost() throws Exception {
        String token = tokenFor(Role.ADMIN);

        call("/api/probe/user", token).andExpect(status().isOk());
        call("/api/probe/admin", token).andExpect(status().isOk());
        call("/api/probe/owner", token).andExpect(status().isForbidden());
    }

    @Test
    void forbiddenAnswerIsAProblemDetail() throws Exception {
        call("/api/probe/admin", tokenFor(Role.USER))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.instance").value("/api/probe/admin"));
    }

    @Test
    void noTokenIsUnauthorizedEvenOnRoleProtectedEndpoints() throws Exception {
        mvc.perform(get("/api/probe/user")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/probe/admin")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRoutesNeedATokenToo() throws Exception {
        mvc.perform(get("/api/does-not-exist")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void registerAndLoginAreOnlyOpenForPost() throws Exception {
        // GET on the open paths is not open: it needs a token
        mvc.perform(get("/api/auth/login")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/register")).andExpect(status().isUnauthorized());
    }

    @Test
    void veryLongPasswordOnLoginIsAnOrdinary401NotAServerError() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nobody@example.com\", \"password\": \"" + "a".repeat(5000) + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void configuredValuesAreWhatWeExpect() {
        assertThat(jwtProperties.expiration()).isEqualTo(Duration.ofHours(24));
        assertThat(termsProperties.version()).isNotBlank();
        assertThat(termsProperties.hostVersion()).isNotBlank();
    }

    @Test
    void tokenIsSignedWithHs256() {
        String token = tokenFor(Role.USER);
        String header = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[0]));

        assertThat(header).contains("\"alg\":\"HS256\"");
    }

    private ResultActions call(String path, String token) throws Exception {
        return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String tokenFor(Role role) {
        User user = new User();
        user.setId(1L);
        user.setEmail("probe@example.com");
        user.setRole(role);
        return jwtService.issue(user).token();
    }
}
