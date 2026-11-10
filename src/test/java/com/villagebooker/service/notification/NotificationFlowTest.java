package com.villagebooker.service.notification;

import com.jayway.jsonpath.JsonPath;
import com.villagebooker.common.time.AppTime;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.controller.booking.BookingTestSupport;
import com.villagebooker.controller.listing.ListingTestSupport;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.enums.notification.EmailStatus;
import com.villagebooker.model.EmailMessage;
import com.villagebooker.repo.notification.EmailMessageRepo;
import com.villagebooker.repo.user.UserRepo;
import com.villagebooker.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static com.villagebooker.controller.booking.BookingTestSupport.OWNER_BOOKINGS;
import static com.villagebooker.controller.booking.BookingTestSupport.idOf;
import static com.villagebooker.controller.listing.ListingTestSupport.OWNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Services publish events in their transaction, and NotificationMails turns each one into emails.
// Tests roll back, so the before-commit listener never runs by itself here: the emails are built by calling
// NotificationMails (or the listener) directly, inside the test's transaction.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class NotificationFlowTest {

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

    @Autowired
    private NotificationMails mails;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private NotificationListener listener;

    @Autowired
    private EmailMessageRepo emailRepo;

    private ListingTestSupport support;
    private BookingTestSupport bookings;
    private String owner;
    private String admin;
    private String guest;

    @BeforeEach
    void setUp() throws Exception {
        support = new ListingTestSupport(mvc, userRepo, jwtService, terms);
        bookings = new BookingTestSupport(mvc);
        owner = support.ownerToken();
        admin = support.adminToken();
        guest = support.userToken();
    }

    @Test
    void aBookingWithADepositMailsBothSidesInTheirLanguage() throws Exception {
        mvc.perform(put("/api/users/me/language").header(HttpHeaders.AUTHORIZATION, "Bearer " + guest)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"language\": \"EN\"}"))
                .andExpect(status().is2xxSuccessful());
        Long listingId = support.create(owner, "/accommodations", BookingTestSupport.depositJson());
        support.approveListing(admin, listingId).andExpect(status().isOk());
        LocalDate arrival = AppTime.today(clock).plusDays(20);

        Long bookingId = bookings.book(guest, listingId, arrival, arrival.plusDays(2), 2);
        List<EmailMessage> created = mails.bookingCreated(single(BookingCreatedEvent.class));
        assertThat(created).extracting(NotificationFlowTest::address).containsExactly(emailOf(guest), emailOf(owner));
        assertThat(created.get(0).getSubject()).isEqualTo("Booking request sent: Brvnara na Tari");
        assertThat(created.get(0).getBody()).startsWith("Hello Ana,")
                .contains("Arrival: " + english(arrival), "Departure: " + english(arrival.plusDays(2)),
                        "Total price: 10000.00 RSD");
        assertThat(created.get(1).getSubject()).isEqualTo("Nova rezervacija čeka vašu potvrdu: Brvnara na Tari");
        assertThat(created.get(1).getBody()).startsWith("Zdravo Ana,").contains("Ana Anić želi da rezerviše")
                .doesNotContain("Napomena gosta");

        bookings.approve(owner, bookingId).andExpect(status().isOk());
        assertThat(mails.bookingStatusChanged(lastStatusChange(BookingStatus.AWAITING_PAYMENT)))
                .singleElement()
                .satisfies(m -> assertThat(m.getBody()).contains("pay the deposit of 3000.00 RSD by"));

        bookings.pay(guest, bookingId).andExpect(status().isOk());
        assertThat(mails.bookingStatusChanged(lastStatusChange(BookingStatus.CONFIRMED)))
                .extracting(NotificationFlowTest::address).containsExactly(emailOf(guest), emailOf(owner));

        mvc.perform(post(OWNER_BOOKINGS + "/" + bookingId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Poplava\"}"))
                .andExpect(status().isOk());
        assertThat(mails.bookingStatusChanged(lastStatusChange(BookingStatus.CANCELLED_BY_OWNER)))
                .singleElement()
                .satisfies(m -> {
                    assertThat(address(m)).isEqualTo(emailOf(guest));
                    assertThat(m.getBody()).contains("Reason: Poplava", "everything you paid: 3000.00 RSD");
                });
    }

    @Test
    void aRejectionWithoutAReasonLeavesTheReasonLineOut() throws Exception {
        Long listingId = support.createAccommodation(owner);
        support.approveListing(admin, listingId).andExpect(status().isOk());
        LocalDate arrival = AppTime.today(clock).plusDays(20);
        Long bookingId = bookings.book(guest, listingId, arrival, arrival.plusDays(1), 2);

        mvc.perform(post(OWNER_BOOKINGS + "/" + bookingId + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isOk());

        assertThat(mails.bookingStatusChanged(lastStatusChange(BookingStatus.REJECTED)))
                .singleElement()
                .satisfies(m -> {
                    assertThat(m.getSubject()).isEqualTo("Domaćin nije prihvatio rezervaciju: Brvnara na Tari");
                    assertThat(m.getBody()).contains("Dolazak: ").doesNotContain("Razlog");
                });
    }

    @Test
    void reachingTheMinimumConfirmsTheTourDateToItsGuests() throws Exception {
        Long tourId = support.createTour(owner);
        support.approveListing(admin, tourId).andExpect(status().isOk());
        LocalDate start = AppTime.today(clock).plusDays(20);
        Long dateId = bookings.createDate(owner, tourId, start);

        bookings.bookTour(guest, dateId, 1).andExpect(status().isCreated());
        assertThat(events.stream(TourDateConfirmedEvent.class)).isEmpty();
        String second = support.userToken();
        bookings.bookTour(second, dateId, 1).andExpect(status().isCreated());

        assertThat(mails.tourDateConfirmed(single(TourDateConfirmedEvent.class)))
                .extracting(NotificationFlowTest::address).containsExactlyInAnyOrder(emailOf(guest), emailOf(second));
        assertThat(mails.tourDateConfirmed(single(TourDateConfirmedEvent.class)).getFirst().getBody())
                .contains("Datum ture: " + serbian(start) + " - " + serbian(start.plusDays(1)));
    }

    @Test
    void theOwnerHearsOnlyAboutTheAdminsDecisions() throws Exception {
        Long listingId = support.createAccommodation(owner);
        support.rejectListing(admin, listingId, "Nedostaju slike").andExpect(status().isOk());
        assertThat(mails.listingStatusChanged(lastListingChange()))
                .singleElement()
                .satisfies(m -> {
                    assertThat(address(m)).isEqualTo(emailOf(owner));
                    assertThat(m.getSubject()).isEqualTo("Oglas nije odobren: Brvnara na Tari");
                    assertThat(m.getBody()).contains("Razlog: Nedostaju slike");
                });

        Long other = support.createAccommodation(owner);
        support.approveListing(admin, other).andExpect(status().isOk());
        assertThat(mails.listingStatusChanged(lastListingChange())).singleElement()
                .satisfies(m -> assertThat(m.getSubject()).isEqualTo("Oglas je odobren: Brvnara na Tari"));

        mvc.perform(post(OWNER + "/" + other + "/pause").header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isOk());
        mvc.perform(post(OWNER + "/" + other + "/resume").header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isOk());
        ListingStatusChangedEvent resume = lastListingChange();
        assertThat(resume.from()).isEqualTo(ListingStatus.PAUSED_BY_OWNER);
        assertThat(mails.listingStatusChanged(resume)).isEmpty();
    }

    @Test
    void theListenerSavesTheEmailsAsPending() throws Exception {
        Long listingId = support.createAccommodation(owner);
        support.approveListing(admin, listingId).andExpect(status().isOk());
        LocalDate arrival = AppTime.today(clock).plusDays(20);
        bookings.book(guest, listingId, arrival, arrival.plusDays(1), 2);

        listener.on(single(BookingCreatedEvent.class));

        Long guestId = userRepo.findByEmail(emailOf(guest)).orElseThrow().getId();
        assertThat(emailRepo.findByRecipientIdOrderById(guestId)).singleElement()
                .satisfies(m -> {
                    assertThat(m.getId()).isNotNull();
                    assertThat(m.getStatus()).isEqualTo(EmailStatus.PENDING);
                    assertThat(m.getSubject()).isEqualTo("Zahtev za rezervaciju je poslat: Brvnara na Tari");
                    assertThat(m.getSentAt()).isNull();
                });
    }

    private static String address(EmailMessage email) {
        return email.getRecipient().getEmail();
    }

    private <T> T single(Class<T> type) {
        return events.stream(type).reduce((a, b) -> {
            throw new AssertionError("More than one " + type.getSimpleName());
        }).orElseThrow();
    }

    private BookingStatusChangedEvent lastStatusChange(BookingStatus to) {
        List<BookingStatusChangedEvent> changes = events.stream(BookingStatusChangedEvent.class).toList();
        assertThat(changes).isNotEmpty();
        BookingStatusChangedEvent last = changes.getLast();
        assertThat(last.to()).isEqualTo(to);
        return last;
    }

    private ListingStatusChangedEvent lastListingChange() {
        return events.stream(ListingStatusChangedEvent.class).toList().getLast();
    }

    private String emailOf(String token) throws Exception {
        ResultActions me = mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        return JsonPath.read(me.andReturn().getResponse().getContentAsString(), "$.email");
    }

    private static String serbian(LocalDate date) {
        return "%02d.%02d.%d.".formatted(date.getDayOfMonth(), date.getMonthValue(), date.getYear());
    }

    private static String english(LocalDate date) {
        return "%02d.%02d.%d".formatted(date.getDayOfMonth(), date.getMonthValue(), date.getYear());
    }
}
