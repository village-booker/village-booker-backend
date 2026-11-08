package com.villagebooker.controller.review;

import com.jayway.jsonpath.JsonPath;
import com.villagebooker.common.time.AppTime;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.controller.booking.BookingTestSupport;
import com.villagebooker.controller.listing.ListingTestSupport;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.enums.review.ReviewReportStatus;
import com.villagebooker.model.Booking;
import com.villagebooker.model.ReviewReport;
import com.villagebooker.repo.booking.BookingRepo;
import com.villagebooker.repo.review.ReviewReportRepo;
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

import static com.villagebooker.controller.booking.BookingTestSupport.BOOKINGS;
import static com.villagebooker.controller.booking.BookingTestSupport.idOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Users report reviews to the admin, who removes the review (reports UPHELD) or dismisses the reports.
// Runs against the configured database, which may hold other reports, so the queue is searched for the test's own
// review; @Transactional rolls every test back.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewReportFlowTest {

    private static final String ADMIN_REVIEWS = "/api/admin/reviews";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private BookingRepo bookingRepo;

    @Autowired
    private ReviewReportRepo reportRepo;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TermsProperties terms;

    @Autowired
    private Clock clock;

    private ListingTestSupport support;
    private BookingTestSupport bookings;
    private String owner;
    private String admin;
    private String author;
    private Long listingId;
    private Long reviewId;

    @BeforeEach
    void setUp() throws Exception {
        support = new ListingTestSupport(mvc, userRepo, jwtService, terms);
        bookings = new BookingTestSupport(mvc);
        owner = support.ownerToken();
        admin = support.adminToken();
        author = support.userToken();
        listingId = support.createAccommodation(owner);
        support.approveListing(admin, listingId).andExpect(status().isOk());
        reviewId = writeReview(author, listingId);
    }

    @Test
    void usersAndTheHostReportAReviewOnceAndTheAdminSeesItInTheQueue() throws Exception {
        String reporter = support.userToken();

        report(reporter, reviewId, "  Lažna recenzija  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewId").value(reviewId))
                .andExpect(jsonPath("$.reason").value("Lažna recenzija"))
                .andExpect(jsonPath("$.status").value("PENDING"));
        report(owner, reviewId, "Gost nikad nije bio kod nas").andExpect(status().isCreated());

        report(reporter, reviewId, "Opet")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_REPORTED"));
        report(author, reviewId, "Moja")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_REPORT_OWN_REVIEW"));
        report(reporter, reviewId, " ").andExpect(status().isBadRequest());
        report(reporter, 999999999L, "Nema je")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
        mvc.perform(post(reportPath(reviewId)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"x\"}"))
                .andExpect(status().isUnauthorized());

        String queue = queue();
        assertThat(this.<Integer>inQueue(queue, "reportCount")).containsExactly(2);
        assertThat(this.<String>inQueue(queue, "reports[0].reason")).containsExactly("Lažna recenzija");
        assertThat(this.<Integer>inQueue(queue, "reports[1].reporterId")).containsExactly(userIdOf(owner).intValue());
        assertThat(this.<Integer>inQueue(queue, "review.listingId")).containsExactly(listingId.intValue());
        assertThat(this.<String>inQueue(queue, "firstReportedAt")).hasSize(1);
    }

    @Test
    void dismissedReportsLeaveTheQueueAndTheReviewStays() throws Exception {
        report(support.userToken(), reviewId, "Uvredljivo").andExpect(status().isCreated());
        report(owner, reviewId, "Netačno").andExpect(status().isCreated());

        dismiss(reviewId).andExpect(status().isNoContent());

        assertThat(this.<Integer>inQueue(queue(), "reportCount")).isEmpty();
        mvc.perform(get("/api/listings/" + listingId + "/reviews"))
                .andExpect(jsonPath("$.content[0].id").value(reviewId));
        assertThat(reports()).hasSize(2)
                .allSatisfy(r -> {
                    assertThat(r.getStatus()).isEqualTo(ReviewReportStatus.DISMISSED);
                    assertThat(r.getResolvedAt()).isNotNull();
                    assertThat(r.getResolvedBy().getId()).isEqualTo(userIdOf(admin));
                });
        dismiss(reviewId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_REPORTED"));
        dismiss(999999999L).andExpect(status().isNotFound());
        // one report per user, also after a decision
        report(owner, reviewId, "Ipak").andExpect(status().isConflict());
        // a new reporter opens it again
        report(support.userToken(), reviewId, "Novo").andExpect(status().isCreated());
        assertThat(this.<Integer>inQueue(queue(), "reportCount")).containsExactly(1);
    }

    @Test
    void removingTheReviewUpholdsItsReports() throws Exception {
        report(support.userToken(), reviewId, "Uvredljivo").andExpect(status().isCreated());

        mvc.perform(delete(ADMIN_REVIEWS + "/" + reviewId).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isNoContent());

        assertThat(reports()).singleElement()
                .satisfies(r -> assertThat(r.getStatus()).isEqualTo(ReviewReportStatus.UPHELD));
        assertThat(this.<Integer>inQueue(queue(), "reportCount")).isEmpty();
        report(support.userToken(), reviewId, "Kasno").andExpect(status().isNotFound());
    }

    @Test
    void reviewsOfARemovedListingLeaveTheQueue() throws Exception {
        report(support.userToken(), reviewId, "Uvredljivo").andExpect(status().isCreated());

        mvc.perform(post(ListingTestSupport.ADMIN + "/" + listingId + "/remove")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Lažan oglas\"}"))
                .andExpect(status().isOk());

        assertThat(this.<Integer>inQueue(queue(), "reportCount")).isEmpty();
        report(support.userToken(), reviewId, "Opet").andExpect(status().isNotFound());
    }

    @Test
    void onlyAnAdminSeesAndDecidesReports() throws Exception {
        mvc.perform(get(ADMIN_REVIEWS + "/reported").header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isForbidden());
        mvc.perform(post(ADMIN_REVIEWS + "/" + reviewId + "/reports/dismiss")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isForbidden());
    }

    // the decision is a bulk update past the persistence context of this test's transaction: read the rows again
    private List<ReviewReport> reports() {
        entityManager.clear();
        return reportRepo.findAll().stream().filter(r -> r.getReview().getId().equals(reviewId)).toList();
    }

    private ResultActions report(String token, Long id, String reason) throws Exception {
        return mvc.perform(post(reportPath(id))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"%s\"}".formatted(reason)));
    }

    private ResultActions dismiss(Long id) throws Exception {
        return mvc.perform(post(ADMIN_REVIEWS + "/" + id + "/reports/dismiss")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin));
    }

    private String queue() throws Exception {
        return mvc.perform(get(ADMIN_REVIEWS + "/reported").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // a field of this test's review in the queue: empty when the review is not in it
    private <T> List<T> inQueue(String queue, String field) {
        return JsonPath.read(queue, "$.content[?(@.review.id == %d)].%s".formatted(reviewId, field));
    }

    private static String reportPath(Long id) {
        return "/api/reviews/" + id + "/reports";
    }

    // a completed stay of the guest (moved into the past as the scheduler would complete it) and its review
    private Long writeReview(String guest, Long listing) throws Exception {
        LocalDate arrival = AppTime.today(clock).plusDays(10);
        Long bookingId = bookings.bookApproved(guest, owner, listing, arrival, arrival.plusDays(2), 2);
        Booking booking = bookingRepo.findById(bookingId).orElseThrow();
        LocalDate lastNight = AppTime.today(clock).minusDays(1);
        booking.setStartDate(lastNight.minusDays(1));
        booking.setEndDate(lastNight);
        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepo.saveAndFlush(booking);
        return idOf(mvc.perform(post(BOOKINGS + "/" + bookingId + "/review")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": 1, \"comment\": \"Užasno\"}"))
                .andExpect(status().isCreated()));
    }

    private Long userIdOf(String token) throws Exception {
        String body = mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
