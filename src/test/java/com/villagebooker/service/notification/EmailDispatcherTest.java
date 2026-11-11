package com.villagebooker.service.notification;

import com.villagebooker.config.TermsProperties;
import com.villagebooker.controller.listing.ListingTestSupport;
import com.villagebooker.enums.notification.EmailStatus;
import com.villagebooker.enums.user.Role;
import com.villagebooker.mail.EmailSendException;
import com.villagebooker.mail.FakeEmailSender;
import com.villagebooker.model.EmailMessage;
import com.villagebooker.model.User;
import com.villagebooker.repo.notification.EmailMessageRepo;
import com.villagebooker.repo.user.UserRepo;
import com.villagebooker.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Emails go out through EmailSender (FakeEmailSender here, Resend in the app); a failed one waits for the admin.
// Tests roll back, so the send after commit (EmailOutbox) never runs here: EmailDispatcher.send is called directly.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmailDispatcherTest {

    private static final String ADMIN_EMAILS = "/api/admin/emails";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EmailDispatcher dispatcher;

    @Autowired
    private EmailOutbox outbox;

    @Autowired
    private FakeEmailSender fake;

    @Autowired
    private EmailMessageRepo emailRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TermsProperties terms;

    private User user;
    private String admin;

    @BeforeEach
    void setUp() {
        fake.reset();
        user = new User();
        user.setEmail("test-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("not-used");
        user.setFirstName("Ana");
        user.setLastName("Anić");
        user.setRole(Role.USER);
        user.setTermsVersion(terms.version());
        user.setTermsAcceptedAt(Instant.now());
        userRepo.saveAndFlush(user);
        admin = new ListingTestSupport(mvc, userRepo, jwtService, terms).adminToken();
    }

    @AfterEach
    void tearDown() {
        fake.reset();
    }

    @Test
    void anEmailIsSentOnceAndMarkedSent() {
        Long id = pending();

        dispatcher.send(id);
        dispatcher.send(id);

        assertThat(fake.sent()).singleElement().satisfies(s -> {
            assertThat(s.to()).isEqualTo(user.getEmail());
            assertThat(s.subject()).isEqualTo("Naslov");
            assertThat(s.text()).isEqualTo("Tekst");
        });
        EmailMessage email = emailRepo.findById(id).orElseThrow();
        assertThat(email.getStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(email.getSentAt()).isNotNull();
    }

    @Test
    void aFailedEmailWaitsForTheAdminWhoSendsItAgain() throws Exception {
        Long id = pending();
        fake.failWith(new EmailSendException("Resend refused the email (422)", null));

        dispatcher.send(id);

        assertThat(emailRepo.findById(id).orElseThrow().getStatus()).isEqualTo(EmailStatus.FAILED);
        mvc.perform(get(ADMIN_EMAILS).param("status", "FAILED").param("recipientId", user.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].recipientEmail").value(user.getEmail()))
                .andExpect(jsonPath("$.content[0].body").doesNotExist());

        fake.reset();
        resend(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.sentAt").isNotEmpty());
        assertThat(fake.sent()).hasSize(1);

        resend(id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_SENT"));
        resend(999999999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_FOUND"));
        assertThat(fake.sent()).hasSize(1);
    }

    @Test
    void anEmailFromARolledBackTransactionIsNeverSent() {
        EmailMessage email = email();

        outbox.add(List.of(email));

        assertThat(email.getId()).isNotNull();
        assertThat(email.getStatus()).isEqualTo(EmailStatus.PENDING);
        // this test's transaction rolls back: afterCommit never runs
        assertThat(fake.sent()).isEmpty();
    }

    @Test
    void onlyAnAdminSeesAndResendsEmails() throws Exception {
        String owner = new ListingTestSupport(mvc, userRepo, jwtService, terms).ownerToken();
        mvc.perform(get(ADMIN_EMAILS).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isForbidden());
        mvc.perform(post(ADMIN_EMAILS + "/" + pending() + "/resend").header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isForbidden());
    }

    private ResultActions resend(Long id) throws Exception {
        return mvc.perform(post(ADMIN_EMAILS + "/" + id + "/resend").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin));
    }

    private Long pending() {
        return emailRepo.saveAndFlush(email()).getId();
    }

    private EmailMessage email() {
        EmailMessage email = new EmailMessage();
        email.setRecipient(user);
        email.setSubject("Naslov");
        email.setBody("Tekst");
        email.setStatus(EmailStatus.PENDING);
        return email;
    }
}
