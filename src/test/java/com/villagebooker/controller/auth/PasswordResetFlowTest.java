package com.villagebooker.controller.auth;

import com.villagebooker.model.EmailMessage;
import com.villagebooker.model.PasswordResetToken;
import com.villagebooker.model.User;
import com.villagebooker.repo.auth.PasswordResetTokenRepo;
import com.villagebooker.repo.notification.EmailMessageRepo;
import com.villagebooker.repo.user.UserRepo;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Forgot password: the link goes to email_message, the token works once and only until it expires.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordResetFlowTest {

    private static final Pattern TOKEN = Pattern.compile("reset-password\\?token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private EmailMessageRepo emailRepo;

    @Autowired
    private PasswordResetTokenRepo tokenRepo;

    @Autowired
    private EntityManager entityManager;

    private String email;
    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        email = "test-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "old-password-1", "firstName": "Ana", "lastName": "Anić",
                                 "acceptTerms": true}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        userId = userRepo.findByEmail(email).orElseThrow().getId();
    }

    @Test
    void theLinkFromTheEmailSetsANewPasswordOnce() throws Exception {
        forgot(email.toUpperCase()).andExpect(status().isNoContent());

        EmailMessage mail = single(emails());
        assertThat(mail.getSubject()).isEqualTo("Nova lozinka za Village Booker");
        assertThat(mail.getBody()).contains("http://localhost:5173/reset-password?token=", "važi do");
        String token = tokenFrom(mail);
        // only the hash is stored
        assertThat(tokenRepo.findAll()).extracting(PasswordResetToken::getTokenHash)
                .doesNotContain(token).anySatisfy(h -> assertThat(h).hasSize(64));

        reset(token, "new-password-2").andExpect(status().isNoContent());
        login("new-password-2").andExpect(status().isOk());
        login("old-password-1").andExpect(status().isUnauthorized());

        reset(token, "third-password-3")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));
    }

    @Test
    void anUnknownEmailGetsTheSameAnswerAndNoEmail() throws Exception {
        long before = emailRepo.count();

        forgot("test-" + UUID.randomUUID() + "@example.com").andExpect(status().isNoContent());

        assertThat(emailRepo.count()).isEqualTo(before);
    }

    @Test
    void aRepeatedRequestWithinAMinuteSendsNoSecondEmail() throws Exception {
        forgot(email).andExpect(status().isNoContent());
        forgot(email).andExpect(status().isNoContent());

        assertThat(emails()).hasSize(1);
    }

    @Test
    void usingOneLinkClosesTheOthers() throws Exception {
        forgot(email).andExpect(status().isNoContent());
        String first = tokenFrom(single(emails()));
        // the minimum interval has passed (created_at is not updatable through the entity)
        entityManager.createNativeQuery(
                        "update password_reset_token set created_at = created_at - interval '1 hour' where user_id = ?")
                .setParameter(1, userId)
                .executeUpdate();
        forgot(email).andExpect(status().isNoContent());
        List<EmailMessage> mails = emails();
        assertThat(mails).hasSize(2);
        String second = tokenFrom(mails.get(1));

        reset(second, "new-password-2").andExpect(status().isNoContent());

        reset(first, "other-password-3").andExpect(status().isBadRequest());
        login("new-password-2").andExpect(status().isOk());
    }

    @Test
    void anExpiredOrWrongLinkIsRefused() throws Exception {
        forgot(email).andExpect(status().isNoContent());
        String token = tokenFrom(single(emails()));
        tokenRepo.findAll().stream().filter(t -> t.getUser().getId().equals(userId))
                .forEach(t -> t.setExpiresAt(Instant.now().minusSeconds(1)));
        tokenRepo.flush();

        reset(token, "new-password-2")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));
        reset("not-a-real-token", "new-password-2").andExpect(status().isBadRequest());
        reset(token, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword.code").value("Size"));
        login("old-password-1").andExpect(status().isOk());
    }

    @Test
    void aBlockedUserGetsNoLink() throws Exception {
        User user = userRepo.findById(userId).orElseThrow();
        user.setBlockedAt(Instant.now());
        userRepo.flush();

        forgot(email).andExpect(status().isNoContent());

        assertThat(emails()).isEmpty();
    }

    private ResultActions forgot(String address) throws Exception {
        return mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\"}".formatted(address)));
    }

    // closing the other links is a bulk update past this test's persistence context: read the rows again
    private ResultActions reset(String token, String password) throws Exception {
        entityManager.clear();
        return mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\", \"newPassword\": \"%s\"}".formatted(token, password)));
    }

    private ResultActions login(String password) throws Exception {
        entityManager.clear();
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    private List<EmailMessage> emails() {
        return emailRepo.findByRecipientIdOrderById(userId);
    }

    private static EmailMessage single(List<EmailMessage> emails) {
        assertThat(emails).hasSize(1);
        return emails.getFirst();
    }

    private static String tokenFrom(EmailMessage email) {
        Matcher m = TOKEN.matcher(email.getBody());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }
}
