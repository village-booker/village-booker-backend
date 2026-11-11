package com.villagebooker.controller.auth;

import com.jayway.jsonpath.JsonPath;
import com.villagebooker.common.time.AppTime;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.controller.booking.BookingTestSupport;
import com.villagebooker.controller.listing.ListingTestSupport;
import com.villagebooker.model.EmailMessage;
import com.villagebooker.repo.notification.EmailMessageRepo;
import com.villagebooker.repo.user.UserRepo;
import com.villagebooker.security.JwtService;
import jakarta.persistence.EntityManager;
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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Registration sends a confirmation link; until it is opened the user can sign in and look around, but not book,
// become a host or report a review.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmailVerificationFlowTest {

    private static final Pattern TOKEN = Pattern.compile("verify-email\\?token=([A-Za-z0-9_.-]+)");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private EmailMessageRepo emailRepo;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TermsProperties terms;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Clock clock;

    private ListingTestSupport support;
    private String token;
    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        support = new ListingTestSupport(mvc, userRepo, jwtService, terms);
        String registered = support.unverifiedUser();
        token = JsonPath.read(registered, "$.accessToken");
        userId = ((Number) JsonPath.read(registered, "$.user.id")).longValue();
        assertThat((Boolean) JsonPath.read(registered, "$.user.emailVerified")).isFalse();
    }

    @Test
    void theLinkFromTheRegistrationEmailConfirmsTheAddress() throws Exception {
        EmailMessage mail = single(emails());
        assertThat(mail.getSubject()).isEqualTo("Potvrdite email adresu za Village Booker");
        assertThat(mail.getBody()).contains("http://localhost:5173/verify-email?token=");
        String link = linkToken(mail);

        verify(link).andExpect(status().isNoContent());
        verify(link).andExpect(status().isNoContent());

        me().andExpect(jsonPath("$.emailVerified").value(true));
        becomeHost().andExpect(status().isOk());
        resend()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_VERIFIED"));
    }

    @Test
    void anUnconfirmedUserSignsInButCannotBookOrBecomeAHost() throws Exception {
        String owner = support.ownerToken();
        Long listingId = support.createAccommodation(owner);
        support.approveListing(support.adminToken(), listingId).andExpect(status().isOk());
        LocalDate arrival = AppTime.today(clock).plusDays(10);

        me().andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(false));
        new BookingTestSupport(mvc).bookAccommodation(token, listingId, arrival, arrival.plusDays(1), 2)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        becomeHost()
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void aWrongOrMisusedTokenIsRefused() throws Exception {
        String link = linkToken(single(emails()));

        verify("not-a-token")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_TOKEN_INVALID"));
        // the login token is signed with another key
        verify(token).andExpect(status().isBadRequest());
        // and the link is not a login token
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + link))
                .andExpect(status().isUnauthorized());
        me().andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    void aNewLinkAtMostOnceAMinute() throws Exception {
        resend().andExpect(status().isNoContent());
        assertThat(emails()).hasSize(1);

        entityManager.createNativeQuery(
                        "update email_message set created_at = created_at - interval '1 hour' where recipient_id = ?")
                .setParameter(1, userId)
                .executeUpdate();
        entityManager.clear();
        resend().andExpect(status().isNoContent());

        List<EmailMessage> mails = emails();
        assertThat(mails).hasSize(2);
        verify(linkToken(mails.get(1))).andExpect(status().isNoContent());
        me().andExpect(jsonPath("$.emailVerified").value(true));
    }

    private ResultActions verify(String link) throws Exception {
        return mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\"}".formatted(link)));
    }

    private ResultActions resend() throws Exception {
        return mvc.perform(post("/api/users/me/verification-email").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions me() throws Exception {
        return mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions becomeHost() throws Exception {
        return mvc.perform(post("/api/users/me/become-host")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bankAccount\": \"160-5100000123456-65\", \"acceptHostTerms\": true}"));
    }

    private List<EmailMessage> emails() {
        return emailRepo.findByRecipientIdOrderById(userId);
    }

    private static EmailMessage single(List<EmailMessage> emails) {
        assertThat(emails).hasSize(1);
        return emails.getFirst();
    }

    private static String linkToken(EmailMessage email) {
        Matcher m = TOKEN.matcher(email.getBody());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }
}
