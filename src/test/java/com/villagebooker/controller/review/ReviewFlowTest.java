package com.villagebooker.controller.review;

import com.villagebooker.common.time.AppTime;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.controller.booking.BookingTestSupport;
import com.villagebooker.controller.listing.ListingTestSupport;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.model.Booking;
import com.villagebooker.repo.booking.BookingRepo;
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

import static com.villagebooker.controller.booking.BookingTestSupport.BOOKINGS;
import static com.villagebooker.controller.booking.BookingTestSupport.idOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Guests review completed bookings, hosts reply, admins remove reviews or replies; the listing's rating follows.
// Runs against the configured database; @Transactional rolls every test back, nothing stays in the DB.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewFlowTest {

    private static final String OWNER_REVIEWS = "/api/owner/reviews";
    private static final String ADMIN_REVIEWS = "/api/admin/reviews";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private BookingRepo bookingRepo;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TermsProperties terms;

    @Autowired
    private Clock clock;

    private ListingTestSupport support;
    private BookingTestSupport bookings;
    private String owner;
    private String admin;
    private LocalDate today;
    private int nextArrival = 10;

    @BeforeEach
    void setUp() throws Exception {
        support = new ListingTestSupport(mvc, userRepo, jwtService, terms);
        bookings = new BookingTestSupport(mvc);
        owner = support.ownerToken();
        admin = support.adminToken();
        today = AppTime.today(clock);
    }

    @Test
    void guestReviewsCompletedStayAndTheListingRatingFollows() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        String guest = support.userToken();
        Long bookingId = completedStay(guest, listingId, today.minusDays(1));

        review(guest, bookingId, 5, "  Divno mesto, domaćini su sjajni.  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").value(bookingId))
                .andExpect(jsonPath("$.listingId").value(listingId))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Divno mesto, domaćini su sjajni."))
                .andExpect(jsonPath("$.guestFirstName").value("Ana"))
                .andExpect(jsonPath("$.ownerReply").value(nullValue()))
                .andExpect(jsonPath("$.deletedAt").value(nullValue()));
        assertRating(listingId, 5.0, 1);

        review(support.userToken(), listingId, 5);
        review(support.userToken(), listingId, 4);
        // (5 + 5 + 4) / 3 = 4.666..., rounded half up
        assertRating(listingId, 4.67, 3);
    }

    @Test
    void tourIsReviewedLikeAStay() throws Exception {
        Long tourId = approved(support.createTour(owner));
        Long dateId = bookings.createDate(owner, tourId, today.plusDays(10));
        String guest = support.userToken();
        Long bookingId = idOf(bookings.bookTour(guest, dateId, 2));
        bookings.approve(owner, bookingId).andExpect(status().isOk());
        complete(bookingId, today.minusDays(3), today.minusDays(2));

        review(guest, bookingId, 3, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comment").value(nullValue()));
        assertRating(tourId, 3.0, 1);
    }

    @Test
    void onlyACompletedBookingCanBeReviewed() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        String guest = support.userToken();
        LocalDate d = today.plusDays(10);
        Long bookingId = bookings.bookApproved(guest, owner, listingId, d, d.plusDays(2), 2);

        review(guest, bookingId, 5, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"))
                .andExpect(jsonPath("$.params.status").value("CONFIRMED"));
        assertRating(listingId, null, 0);
    }

    // 14 days from the day after the last booked date (the check-out day), that day included
    @Test
    void reviewCanBeWrittenWithinFourteenDaysAfterTheStay() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        String lastDayGuest = support.userToken();
        Long lastDay = completedStay(lastDayGuest, listingId, today.minusDays(15));
        String lateGuest = support.userToken();
        Long late = completedStay(lateGuest, listingId, today.minusDays(16));

        review(lastDayGuest, lastDay, 4, null).andExpect(status().isCreated());
        review(lateGuest, late, 4, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_PERIOD_OVER"))
                .andExpect(jsonPath("$.params.days").value(14))
                .andExpect(jsonPath("$.params.lastDay").value(today.minusDays(1).toString()));
    }

    @Test
    void aBookingHasOneReview() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        String guest = support.userToken();
        Long bookingId = completedStay(guest, listingId, today.minusDays(1));
        review(guest, bookingId, 5, null).andExpect(status().isCreated());

        review(guest, bookingId, 1, "Ipak ne")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"));
        assertRating(listingId, 5.0, 1);
    }

    @Test
    void anotherGuestsBookingIsNotFound() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        Long bookingId = completedStay(support.userToken(), listingId, today.minusDays(1));
        String stranger = support.userToken();

        review(stranger, bookingId, 1, "Nisam ni bio tamo")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
        getReview(stranger, bookingId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
        // not even the host of the listing can review it in the guest's place
        review(owner, bookingId, 5, null).andExpect(status().isNotFound());
        assertRating(listingId, null, 0);
    }

    @Test
    void ratingAndCommentAreValidated() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        String guest = support.userToken();
        Long bookingId = completedStay(guest, listingId, today.minusDays(1));

        review(guest, bookingId, 0, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rating.code").value("Min"));
        review(guest, bookingId, 6, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rating.code").value("Max"));
        review(guest, bookingId, 5, "a".repeat(2001))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.comment.code").value("Size"));
        mvc.perform(post(reviewPath(bookingId)).header(HttpHeaders.AUTHORIZATION, "Bearer " + guest)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rating.code").value("NotNull"));

        // the longest comment fits the column; a blank one is stored as no comment
        review(guest, bookingId, 5, "a".repeat(2000)).andExpect(status().isCreated());
        Long other = completedStay(guest, listingId, today.minusDays(5));
        review(guest, other, 5, "   ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comment").value(nullValue()));
    }

    @Test
    void guestReadsOwnReview() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        String guest = support.userToken();
        Long bookingId = completedStay(guest, listingId, today.minusDays(1));

        getReview(guest, bookingId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
        Long reviewId = idOf(review(guest, bookingId, 4, "Dobro"));

        getReview(guest, bookingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reviewId))
                .andExpect(jsonPath("$.comment").value("Dobro"));
    }

    @Test
    void listingPageShowsReviewsWithoutLoginAndWithoutContactData() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        Long older = review(support.userToken(), listingId, 4);
        Long newer = review(support.userToken(), listingId, 5);

        mvc.perform(get(publicPath(listingId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(newer))
                .andExpect(jsonPath("$.content[1].id").value(older))
                .andExpect(jsonPath("$.content[0].guestFirstName").value("Ana"))
                .andExpect(jsonPath("$.content[0].stayStartDate").exists())
                .andExpect(content().string(not(containsString("@example.com"))))
                .andExpect(content().string(not(containsString("bookingId"))));

        // paused: the page is still open, so are its reviews
        mvc.perform(post(ListingTestSupport.OWNER + "/" + listingId + "/pause")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isOk());
        mvc.perform(get(publicPath(listingId))).andExpect(status().isOk());
    }

    @Test
    void listingGuestsCannotOpenHasNoPublicReviews() throws Exception {
        Long pending = support.createAccommodation(owner);

        mvc.perform(get(publicPath(pending)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
        mvc.perform(get(publicPath(999_999_999L))).andExpect(status().isNotFound());
    }

    @Test
    void hostRepliesOnceAndEveryoneSeesTheReply() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        String guest = support.userToken();
        Long bookingId = completedStay(guest, listingId, today.minusDays(1));
        Long reviewId = idOf(review(guest, bookingId, 3, "Hladno u sobi"));

        reply(owner, reviewId, "  Hvala, popravili smo grejanje.  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerReply").value("Hvala, popravili smo grejanje."))
                .andExpect(jsonPath("$.ownerReplyAt").exists());
        reply(owner, reviewId, "Druga verzija")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_REPLIED"));

        mvc.perform(get(publicPath(listingId)))
                .andExpect(jsonPath("$.content[0].ownerReply").value("Hvala, popravili smo grejanje."));
        getReview(guest, bookingId)
                .andExpect(jsonPath("$.ownerReply").value("Hvala, popravili smo grejanje."));
    }

    @Test
    void hostRepliesOnlyToReviewsOfOwnListings() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        Long reviewId = review(support.userToken(), listingId, 2);
        String otherHost = support.ownerToken();

        reply(otherHost, reviewId, "Nije moj oglas")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
        reply(owner, reviewId, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reply.code").value("NotBlank"));
        reply(owner, reviewId, "a".repeat(2001))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reply.code").value("Size"));
        // a guest is not a host
        reply(support.userToken(), reviewId, "Ja sam gost").andExpect(status().isForbidden());
    }

    @Test
    void hostSeesReviewsOfOwnListings() throws Exception {
        Long first = approved(support.createAccommodation(owner));
        Long second = approved(support.createAccommodation(owner));
        Long a = review(support.userToken(), first, 5);
        Long b = review(support.userToken(), second, 4);
        String otherHost = support.ownerToken();
        Long foreign = approved(support.createAccommodation(otherHost));
        String guest = support.userToken();
        review(guest, completedStay(guest, otherHost, foreign, today.minusDays(1)), 1, null)
                .andExpect(status().isCreated());

        ownerReviews(owner, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(b))
                .andExpect(jsonPath("$.content[1].id").value(a));
        ownerReviews(owner, "?listingId=" + first)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(a))
                .andExpect(jsonPath("$.content[0].listingName").exists());
    }

    @Test
    void adminRemovesAReviewAndTheRatingIsRecomputed() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        review(support.userToken(), listingId, 5);
        String guest = support.userToken();
        Long bookingId = completedStay(guest, listingId, today.minusDays(1));
        Long offensive = idOf(review(guest, bookingId, 1, "Uvredljiv tekst"));
        assertRating(listingId, 3.0, 2);

        adminDelete(ADMIN_REVIEWS + "/" + offensive).andExpect(status().isNoContent());

        assertRating(listingId, 5.0, 1);
        mvc.perform(get(publicPath(listingId)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(not(offensive.intValue())));
        ownerReviews(owner, "").andExpect(jsonPath("$.totalElements").value(1));
        reply(owner, offensive, "Odgovor").andExpect(status().isNotFound());
        // the guest sees it was removed and cannot write a new one for the same booking
        getReview(guest, bookingId).andExpect(jsonPath("$.deletedAt").exists());
        review(guest, bookingId, 5, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"));
        // the admin still sees it
        mvc.perform(get(ADMIN_REVIEWS + "?listingId=" + listingId).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(offensive))
                .andExpect(jsonPath("$.content[0].deletedAt").exists());

        // again: nothing changes
        adminDelete(ADMIN_REVIEWS + "/" + offensive).andExpect(status().isNoContent());
        assertRating(listingId, 5.0, 1);
    }

    @Test
    void removingTheOnlyReviewClearsTheRating() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        Long reviewId = review(support.userToken(), listingId, 4);

        adminDelete(ADMIN_REVIEWS + "/" + reviewId).andExpect(status().isNoContent());

        assertRating(listingId, null, 0);
        adminDelete(ADMIN_REVIEWS + "/999999999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
    }

    @Test
    void adminRemovesOnlyTheReplyAndTheHostMayReplyAgain() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        Long reviewId = review(support.userToken(), listingId, 2);
        reply(owner, reviewId, "Uvredljiv odgovor").andExpect(status().isOk());

        adminDelete(ADMIN_REVIEWS + "/" + reviewId + "/reply").andExpect(status().isNoContent());

        mvc.perform(get(publicPath(listingId)))
                .andExpect(jsonPath("$.content[0].id").value(reviewId))
                .andExpect(jsonPath("$.content[0].ownerReply").value(nullValue()))
                .andExpect(jsonPath("$.content[0].ownerReplyAt").value(nullValue()));
        assertRating(listingId, 2.0, 1);
        reply(owner, reviewId, "Izvinjavam se, pristojan odgovor").andExpect(status().isOk());
    }

    @Test
    void onlyAnAdminRemovesReviews() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        Long reviewId = review(support.userToken(), listingId, 1);

        mvc.perform(delete(ADMIN_REVIEWS + "/" + reviewId).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isForbidden());
        mvc.perform(delete(ADMIN_REVIEWS + "/" + reviewId + "/reply")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + support.userToken()))
                .andExpect(status().isForbidden());
        mvc.perform(get(ADMIN_REVIEWS).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isForbidden());
        assertRating(listingId, 1.0, 1);
    }

    // a new guest with a completed stay on the listing writes a review -> its id
    private Long review(String guest, Long listingId, int rating) throws Exception {
        Long bookingId = completedStay(guest, listingId, today.minusDays(1));
        return idOf(review(guest, bookingId, rating, null).andExpect(status().isCreated()));
    }

    // booked for free future nights and approved by the host, then moved into the past and completed as the scheduler
    // would do it (BookingJobsFlowTest covers that step)
    private Long completedStay(String guest, Long listingId, LocalDate lastNight) throws Exception {
        return completedStay(guest, owner, listingId, lastNight);
    }

    private Long completedStay(String guest, String host, Long listingId, LocalDate lastNight) throws Exception {
        LocalDate arrival = today.plusDays(nextArrival);
        nextArrival += 3;
        Long bookingId = bookings.bookApproved(guest, host, listingId, arrival, arrival.plusDays(2), 2);
        complete(bookingId, lastNight.minusDays(1), lastNight);
        return bookingId;
    }

    private void complete(Long bookingId, LocalDate start, LocalDate end) {
        Booking booking = bookingRepo.findById(bookingId).orElseThrow();
        booking.setStartDate(start);
        booking.setEndDate(end);
        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepo.saveAndFlush(booking);
    }

    private Long approved(Long listingId) throws Exception {
        support.approveListing(admin, listingId).andExpect(status().isOk());
        return listingId;
    }

    private ResultActions review(String token, Long bookingId, int rating, String comment) throws Exception {
        String json = comment == null
                ? "{\"rating\": %d}".formatted(rating)
                : "{\"rating\": %d, \"comment\": \"%s\"}".formatted(rating, comment);
        return mvc.perform(post(reviewPath(bookingId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions getReview(String token, Long bookingId) throws Exception {
        return mvc.perform(get(reviewPath(bookingId)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions reply(String token, Long reviewId, String reply) throws Exception {
        return mvc.perform(post(OWNER_REVIEWS + "/" + reviewId + "/reply")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reply\": \"%s\"}".formatted(reply)));
    }

    private ResultActions ownerReviews(String token, String query) throws Exception {
        return mvc.perform(get(OWNER_REVIEWS + query).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions adminDelete(String path) throws Exception {
        return mvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin));
    }

    // rating and reviewCount as guests see them on the listing page
    private void assertRating(Long listingId, Double rating, int count) throws Exception {
        ResultActions page = mvc.perform(get("/api/listings/" + listingId))
                .andExpect(jsonPath("$.reviewCount").value(count));
        if (rating == null) {
            page.andExpect(jsonPath("$.rating").value(nullValue()));
        } else {
            page.andExpect(jsonPath("$.rating").value(rating));
        }
    }

    private static String reviewPath(Long bookingId) {
        return BOOKINGS + "/" + bookingId + "/review";
    }

    private static String publicPath(Long listingId) {
        return "/api/listings/" + listingId + "/reviews";
    }
}
