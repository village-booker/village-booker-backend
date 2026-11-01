package com.villagebooker.controller.listing;

import com.villagebooker.common.time.AppTime;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.controller.booking.BookingTestSupport;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.enums.payment.PaymentStatus;
import com.villagebooker.enums.tour.TourDateStatus;
import com.villagebooker.model.Booking;
import com.villagebooker.model.ListingStatusHistory;
import com.villagebooker.model.Payment;
import com.villagebooker.model.TourDate;
import com.villagebooker.repo.booking.BookingRepo;
import com.villagebooker.repo.listing.ListingRepo;
import com.villagebooker.repo.listing.ListingStatusHistoryRepo;
import com.villagebooker.repo.payment.PaymentRepo;
import com.villagebooker.repo.tour.TourDateRepo;
import com.villagebooker.repo.user.UserRepo;
import com.villagebooker.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static com.villagebooker.controller.booking.BookingTestSupport.idOf;
import static com.villagebooker.controller.listing.ListingTestSupport.ADMIN_CHANGE_REQUESTS;
import static com.villagebooker.controller.listing.ListingTestSupport.ADMIN_IMAGES;
import static com.villagebooker.controller.listing.ListingTestSupport.OWNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The host pauses, resumes and deletes own listings (spec 9.3).
// Runs against the configured database; @Transactional rolls every test back, nothing stays in the DB.
@SpringBootTest(properties = "app.payments.simulated=true")
@AutoConfigureMockMvc
@Transactional
class ListingLifecycleFlowTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private ListingRepo listingRepo;

    @Autowired
    private ListingStatusHistoryRepo historyRepo;

    @Autowired
    private BookingRepo bookingRepo;

    @Autowired
    private TourDateRepo tourDateRepo;

    @Autowired
    private PaymentRepo paymentRepo;

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
    private String guest;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        support = new ListingTestSupport(mvc, userRepo, jwtService, terms);
        bookings = new BookingTestSupport(mvc);
        owner = support.ownerToken();
        admin = support.adminToken();
        guest = support.userToken();
        today = AppTime.today(clock);
    }

    @Test
    void pausedListingTakesNoNewBookingsButKeepsTheOldOnes() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        LocalDate d = today.plusDays(10);
        Long request = bookings.book(guest, listingId, d, d.plusDays(1), 2);

        pause(owner, listingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED_BY_OWNER"));

        bookings.bookAccommodation(support.userToken(), listingId, d.plusDays(5), d.plusDays(6), 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_BOOKABLE"));
        // guests who booked still see the page, the host still answers their requests
        mvc.perform(get("/api/listings/" + listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookable").value(false));
        bookings.approve(owner, request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));
    }

    @Test
    void resumedListingTakesBookingsAgain() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        pause(owner, listingId).andExpect(status().isOk());

        resume(owner, listingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        LocalDate d = today.plusDays(10);
        bookings.bookAccommodation(guest, listingId, d, d.plusDays(1), 2).andExpect(status().isCreated());
        mvc.perform(get("/api/listings/" + listingId)).andExpect(jsonPath("$.bookable").value(true));
        List<ListingStatusHistory> history = history(listingId);
        assertThat(history).extracting(ListingStatusHistory::getNewStatus)
                .containsExactly(ListingStatus.APPROVED, ListingStatus.PAUSED_BY_OWNER, ListingStatus.APPROVED);
        Long ownerId = listingRepo.findById(listingId).orElseThrow().getOwner().getId();
        assertThat(history.getLast().getChangedBy().getId()).isEqualTo(ownerId);
    }

    @Test
    void onlyAnApprovedListingIsPausedAndOnlyAPausedOneResumed() throws Exception {
        Long pending = support.createAccommodation(owner);
        pause(owner, pending)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_PAUSABLE"))
                .andExpect(jsonPath("$.params.status").value("PENDING"));

        Long listingId = approved(support.createAccommodation(owner));
        resume(owner, listingId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_PAUSED"))
                .andExpect(jsonPath("$.params.status").value("APPROVED"));
        pause(owner, listingId).andExpect(status().isOk());
        pause(owner, listingId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.params.status").value("PAUSED_BY_OWNER"));
    }

    @Test
    void onlyTheOwnerPausesOrDeletes() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        String otherHost = support.ownerToken();

        pause(otherHost, listingId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_LISTING_OWNER"));
        deleteListing(otherHost, listingId, false).andExpect(status().isForbidden());
        pause(guest, listingId).andExpect(status().isForbidden());
        deleteListing(guest, listingId, false).andExpect(status().isForbidden());
        pause(owner, 999999999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
        assertThat(listingRepo.findById(listingId).orElseThrow().getStatus()).isEqualTo(ListingStatus.APPROVED);
    }

    @Test
    void listingWithoutBookingsIsDeletedAtOnce() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));

        deleteListing(owner, listingId, false).andExpect(status().isNoContent());

        assertThat(listingRepo.findById(listingId).orElseThrow().getStatus()).isEqualTo(ListingStatus.DELETED);
        assertThat(history(listingId).getLast().getNewStatus()).isEqualTo(ListingStatus.DELETED);
        mvc.perform(get(OWNER + "/" + listingId).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/listings/" + listingId)).andExpect(status().isNotFound());
        mvc.perform(get(OWNER).param("size", "100").header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(listingId)).value(empty()));
        deleteListing(owner, listingId, false).andExpect(status().isNotFound());
        pause(owner, listingId).andExpect(status().isNotFound());
    }

    @Test
    void pendingAndPausedListingsCanBeDeletedToo() throws Exception {
        Long pending = support.createAccommodation(owner);
        deleteListing(owner, pending, false).andExpect(status().isNoContent());

        Long paused = approved(support.createAccommodation(owner));
        pause(owner, paused).andExpect(status().isOk());
        deleteListing(owner, paused, false).andExpect(status().isNoContent());

        assertThat(listingRepo.findById(pending).orElseThrow().getStatus()).isEqualTo(ListingStatus.DELETED);
        assertThat(listingRepo.findById(paused).orElseThrow().getStatus()).isEqualTo(ListingStatus.DELETED);
    }

    @Test
    void upcomingBookingsNeedAConfirmationAndAreThenCancelledWithAFullRefund() throws Exception {
        // free cancellation until 7 days before: 3 days before, a guest would get nothing back
        Long listingId = approved(support.create(owner, "/accommodations", BookingTestSupport.depositJson()));
        LocalDate soon = today.plusDays(3);
        Long paid = bookings.bookApproved(guest, owner, listingId, soon, soon.plusDays(2), 2);
        bookings.pay(guest, paid).andExpect(jsonPath("$.status").value("CONFIRMED"));
        Long request = bookings.book(support.userToken(), listingId, today.plusDays(20), today.plusDays(21), 2);
        Long declined = bookings.book(support.userToken(), listingId, today.plusDays(30), today.plusDays(31), 2);
        mvc.perform(post(BookingTestSupport.OWNER_BOOKINGS + "/" + declined + "/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner)).andExpect(status().isOk());

        deleteListing(owner, listingId, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_HAS_ACTIVE_BOOKINGS"))
                .andExpect(jsonPath("$.params.activeBookings").value(2))
                .andExpect(jsonPath("$.params.bookingIds[0]").value(paid))
                .andExpect(jsonPath("$.params.bookingIds[1]").value(request));
        // nothing changed
        assertThat(listingRepo.findById(listingId).orElseThrow().getStatus()).isEqualTo(ListingStatus.APPROVED);
        assertThat(statusOf(paid)).isEqualTo(BookingStatus.CONFIRMED);

        deleteListing(owner, listingId, true).andExpect(status().isNoContent());

        assertThat(listingRepo.findById(listingId).orElseThrow().getStatus()).isEqualTo(ListingStatus.DELETED);
        for (Long id : List.of(paid, request)) {
            Booking booking = bookingRepo.findById(id).orElseThrow();
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED_BY_OWNER);
            assertThat(booking.getCancelledAt()).isNotNull();
            assertThat(booking.getCancellationReason()).isNull();
            assertThat(booking.getExpiresAt()).isNull();
        }
        assertThat(statusOf(declined)).isEqualTo(BookingStatus.REJECTED);
        Payment payment = paymentRepo.findByBookingId(paid).getFirst();
        // 30 % of 10000, all of it back: the host cancelled
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundAmount()).isEqualByComparingTo("3000");
        // the guest still sees the booking of the deleted listing
        mvc.perform(get(BookingTestSupport.BOOKINGS + "/" + paid).header(HttpHeaders.AUTHORIZATION, "Bearer " + guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED_BY_OWNER"));
    }

    @Test
    void stayThatHasBegunStaysWhenTheListingIsDeleted() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        Long current = bookings.bookApproved(guest, owner, listingId, today.plusDays(1), today.plusDays(3), 2);
        // the guest arrived yesterday and leaves tomorrow
        dates(current, today.minusDays(1), today);
        Long upcoming = bookings.bookApproved(support.userToken(), owner, listingId,
                today.plusDays(10), today.plusDays(11), 2);

        deleteListing(owner, listingId, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.params.activeBookings").value(1))
                .andExpect(jsonPath("$.params.bookingIds[0]").value(upcoming));
        deleteListing(owner, listingId, true).andExpect(status().isNoContent());

        assertThat(statusOf(current)).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(statusOf(upcoming)).isEqualTo(BookingStatus.CANCELLED_BY_OWNER);
    }

    @Test
    void onlyAStayThatHasBegunDoesNotNeedAConfirmation() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        Long current = bookings.bookApproved(guest, owner, listingId, today.plusDays(1), today.plusDays(3), 2);
        dates(current, today, today.plusDays(1));

        deleteListing(owner, listingId, false).andExpect(status().isNoContent());

        assertThat(statusOf(current)).isEqualTo(BookingStatus.CONFIRMED);
        // the host still manages it
        mvc.perform(get(BookingTestSupport.OWNER_BOOKINGS + "/" + current)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isOk());
    }

    @Test
    void deletingATourCancelsItsUpcomingDates() throws Exception {
        Long tourId = approved(support.createTour(owner));
        Long booked = bookings.createDate(owner, tourId, today.plusDays(10));
        Long first = idOf(bookings.bookTour(guest, booked, 2).andExpect(status().isCreated()));
        Long second = idOf(bookings.bookTour(support.userToken(), booked, 3).andExpect(status().isCreated()));
        Long empty = bookings.createDate(owner, tourId, today.plusDays(20));
        Long running = bookings.createDate(owner, tourId, today.plusDays(30));
        Long onTour = idOf(bookings.bookTour(support.userToken(), running, 2).andExpect(status().isCreated()));
        bookings.approve(owner, onTour).andExpect(status().isOk());
        // the 2-day tour started yesterday and ends today
        TourDate date = tourDateRepo.findById(running).orElseThrow();
        date.setStartDate(today.minusDays(1));
        date.setEndDate(today);
        tourDateRepo.saveAndFlush(date);
        dates(onTour, today.minusDays(1), today);

        deleteListing(owner, tourId, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.params.activeBookings").value(2));
        deleteListing(owner, tourId, true).andExpect(status().isNoContent());

        assertDate(booked, 0, TourDateStatus.CANCELLED);
        assertDate(empty, 0, TourDateStatus.CANCELLED);
        assertDate(running, 2, TourDateStatus.CONFIRMED);
        assertThat(statusOf(first)).isEqualTo(BookingStatus.CANCELLED_BY_OWNER);
        assertThat(statusOf(second)).isEqualTo(BookingStatus.CANCELLED_BY_OWNER);
        assertThat(statusOf(onTour)).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void deletedListingLeavesTheAdminQueues() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        Long requestId = idOf(mvc.perform(post(OWNER + "/accommodations/" + listingId + "/change-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ListingTestSupport.accommodationJson().replace("Brvnara na Tari", "Nova brvnara")))
                .andExpect(status().isCreated()));
        Long imageId = idOf(mvc.perform(multipart(OWNER + "/" + listingId + "/images")
                        .file(new MockMultipartFile("file", "image.png", "image/png", PNG))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(jsonPath("$.status").value("PENDING")));
        inQueue(ADMIN_CHANGE_REQUESTS, requestId).andExpect(jsonPath(itemPath(requestId)).value(not(empty())));
        inQueue(ADMIN_IMAGES, imageId).andExpect(jsonPath(itemPath(imageId)).value(not(empty())));

        deleteListing(owner, listingId, false).andExpect(status().isNoContent());

        inQueue(ADMIN_CHANGE_REQUESTS, requestId).andExpect(jsonPath(itemPath(requestId)).value(empty()));
        inQueue(ADMIN_IMAGES, imageId).andExpect(jsonPath(itemPath(imageId)).value(empty()));
    }

    private Long approved(Long listingId) throws Exception {
        support.approveListing(admin, listingId).andExpect(status().isOk());
        return listingId;
    }

    private ResultActions pause(String token, Long listingId) throws Exception {
        return mvc.perform(post(OWNER + "/" + listingId + "/pause").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions resume(String token, Long listingId) throws Exception {
        return mvc.perform(post(OWNER + "/" + listingId + "/resume").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions deleteListing(String token, Long listingId, boolean cancelBookings) throws Exception {
        return mvc.perform(delete(OWNER + "/" + listingId)
                .param("cancelBookings", String.valueOf(cancelBookings))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions inQueue(String path, Long id) throws Exception {
        return mvc.perform(get(path).param("size", "100").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk());
    }

    private static String itemPath(Long id) {
        return "$.content[?(@.id == %d)]".formatted(id);
    }

    // oldest first (by id: rows written in one test can have the same changedAt)
    private List<ListingStatusHistory> history(Long listingId) {
        return historyRepo.findByListingIdOrderByChangedAtDesc(listingId, Pageable.unpaged()).stream()
                .sorted(Comparator.comparing(ListingStatusHistory::getId))
                .toList();
    }

    private void dates(Long bookingId, LocalDate start, LocalDate end) {
        Booking booking = bookingRepo.findById(bookingId).orElseThrow();
        booking.setStartDate(start);
        booking.setEndDate(end);
        bookingRepo.saveAndFlush(booking);
    }

    private BookingStatus statusOf(Long bookingId) {
        return bookingRepo.findById(bookingId).orElseThrow().getStatus();
    }

    private void assertDate(Long dateId, int bookedCount, TourDateStatus status) {
        TourDate date = tourDateRepo.findById(dateId).orElseThrow();
        assertThat(date.getBookedCount()).isEqualTo(bookedCount);
        assertThat(date.getStatus()).isEqualTo(status);
    }
}
