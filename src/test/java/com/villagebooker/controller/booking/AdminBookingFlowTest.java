package com.villagebooker.controller.booking;

import com.jayway.jsonpath.JsonPath;
import com.villagebooker.common.time.AppTime;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.controller.listing.ListingTestSupport;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The admin's view of all bookings and of cancellations by hosts (spec 9.4). Runs against the configured
// database, which may hold other bookings, so the lists are filtered by the test's own users;
// @Transactional rolls every test back.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminBookingFlowTest {

    private static final String ADMIN_BOOKINGS = "/api/admin/bookings";

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
    private String admin;
    private String host;
    private String guest;
    private LocalDate d;

    @BeforeEach
    void setUp() throws Exception {
        support = new ListingTestSupport(mvc, userRepo, jwtService, terms);
        bookings = new BookingTestSupport(mvc);
        admin = support.adminToken();
        host = support.ownerToken();
        guest = support.userToken();
        d = AppTime.today(clock).plusDays(20);
    }

    @Test
    void adminSeesEveryBookingWithBothSidesAndPayments() throws Exception {
        Long cabin = approved(support.create(host, "/accommodations", BookingTestSupport.depositJson()));
        Long paid = bookings.bookApproved(guest, host, cabin, d, d.plusDays(2), 2);
        bookings.pay(guest, paid).andExpect(status().isOk());
        Long request = bookings.book(guest, cabin, d.plusDays(5), d.plusDays(6), 2);
        String otherHost = support.ownerToken();
        Long otherCabin = approved(support.createAccommodation(otherHost));
        Long other = bookings.book(guest, otherCabin, d, d.plusDays(1), 2);

        mvc.perform(get(ADMIN_BOOKINGS + "/" + paid).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.booking.listingId").value(cabin))
                .andExpect(jsonPath("$.guest.id").value(idOf(guest)))
                .andExpect(jsonPath("$.guest.email").exists())
                .andExpect(jsonPath("$.owner.id").value(idOf(host)))
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.payments[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.payments[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.payments[0].refundAmount").value(0));

        list("?guestId=" + idOf(guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].booking.id").value(other))
                .andExpect(jsonPath("$.content[0].owner.id").value(idOf(otherHost)))
                .andExpect(jsonPath("$.content[0].payments").doesNotExist());
        list("?ownerId=" + idOf(host))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].booking.id").value(request))
                .andExpect(jsonPath("$.content[1].booking.id").value(paid));
        list("?listingId=" + cabin + "&status=AWAITING_OWNER_APPROVAL")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].booking.id").value(request));
        list("?status=NOT_A_STATUS").andExpect(status().isBadRequest());

        mvc.perform(get(ADMIN_BOOKINGS + "/" + Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void hostsAreListedByCancellationsInThePeriod() throws Exception {
        Long cabin = approved(support.createAccommodation(host));
        Long first = bookings.bookApproved(guest, host, cabin, d, d.plusDays(1), 2);
        Long second = bookings.book(guest, cabin, d.plusDays(3), d.plusDays(4), 2);
        Long old = bookings.book(guest, cabin, d.plusDays(6), d.plusDays(7), 2);
        cancelAsHost(host, first);
        cancelAsHost(host, second);
        cancelAsHost(host, old);
        String otherHost = support.ownerToken();
        Long otherCabin = approved(support.createAccommodation(otherHost));
        cancelAsHost(otherHost, bookings.book(guest, otherCabin, d, d.plusDays(1), 2));
        // a guest's own cancellation does not count
        Long byGuest = bookings.book(guest, otherCabin, d.plusDays(3), d.plusDays(4), 2);
        mvc.perform(post("/api/bookings/" + byGuest + "/cancel").header(HttpHeaders.AUTHORIZATION, "Bearer " + guest))
                .andExpect(status().isOk());
        Booking oldBooking = bookingRepo.findById(old).orElseThrow();
        oldBooking.setCancelledAt(AppTime.startOfDay(AppTime.today(clock).minusMonths(7)));
        bookingRepo.flush();

        String body = cancellations("?size=100").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(countFor(body, idOf(host))).containsExactly(2);
        assertThat(countFor(body, idOf(otherHost))).containsExactly(1);
        assertThat(JsonPath.<List<String>>read(body, "$.content[?(@.ownerId == " + idOf(host) + ")].email"))
                .hasSize(1);

        body = cancellations("?months=12&size=100").andReturn().getResponse().getContentAsString();
        assertThat(countFor(body, idOf(host))).containsExactly(3);
        // out of range -> clamped to 1..24
        body = cancellations("?months=0&size=100").andReturn().getResponse().getContentAsString();
        assertThat(countFor(body, idOf(host))).containsExactly(2);
    }

    @Test
    void onlyAdminsSeeAllBookings() throws Exception {
        mvc.perform(get(ADMIN_BOOKINGS).header(HttpHeaders.AUTHORIZATION, "Bearer " + host))
                .andExpect(status().isForbidden());
        mvc.perform(get(ADMIN_BOOKINGS + "/owner-cancellations").header(HttpHeaders.AUTHORIZATION, "Bearer " + guest))
                .andExpect(status().isForbidden());
    }

    private Long approved(Long listingId) throws Exception {
        support.approveListing(admin, listingId).andExpect(status().isOk());
        return listingId;
    }

    private void cancelAsHost(String ownerToken, Long bookingId) throws Exception {
        mvc.perform(post(BookingTestSupport.OWNER_BOOKINGS + "/" + bookingId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Double booked\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions list(String query) throws Exception {
        return mvc.perform(get(ADMIN_BOOKINGS + query).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin));
    }

    private ResultActions cancellations(String query) throws Exception {
        return mvc.perform(get(ADMIN_BOOKINGS + "/owner-cancellations" + query)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin));
    }

    private static List<Integer> countFor(String body, Long ownerId) {
        return JsonPath.read(body, "$.content[?(@.ownerId == " + ownerId + ")].cancellations");
    }

    private Long idOf(String token) throws Exception {
        String body = mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
