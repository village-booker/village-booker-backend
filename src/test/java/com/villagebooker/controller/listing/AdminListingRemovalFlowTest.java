package com.villagebooker.controller.listing;

import com.villagebooker.common.time.AppTime;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.controller.booking.BookingTestSupport;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.model.ListingStatusHistory;
import com.villagebooker.repo.booking.BookingRepo;
import com.villagebooker.repo.listing.ListingRepo;
import com.villagebooker.repo.listing.ListingStatusHistoryRepo;
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
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

import static com.villagebooker.controller.booking.BookingTestSupport.idOf;
import static com.villagebooker.controller.listing.ListingTestSupport.ADMIN;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The admin removes a listing in any status (REMOVED_BY_ADMIN): out of the search at once, existing bookings go on,
// the host sees it with the reason but adds nothing to it. Runs against the configured database; rolled back.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminListingRemovalFlowTest {

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
    private LocalDate d;

    @BeforeEach
    void setUp() throws Exception {
        support = new ListingTestSupport(mvc, userRepo, jwtService, terms);
        bookings = new BookingTestSupport(mvc);
        owner = support.ownerToken();
        admin = support.adminToken();
        guest = support.userToken();
        d = AppTime.today(clock).plusDays(10);
    }

    @Test
    void removedListingLeavesTheSearchButItsBookingsGoOn() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        Long booking = bookings.bookApproved(guest, owner, listingId, d, d.plusDays(2), 2);

        remove(listingId, "  Misleading photos  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listing.status").value("REMOVED_BY_ADMIN"))
                .andExpect(jsonPath("$.listing.removalReason").value("Misleading photos"));

        mvc.perform(get("/api/listings/" + listingId)).andExpect(status().isNotFound());
        bookings.bookAccommodation(support.userToken(), listingId, d.plusDays(5), d.plusDays(6), 2)
                .andExpect(status().isNotFound());
        assertThat(bookingRepo.findById(booking).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        mvc.perform(get(BookingTestSupport.OWNER_BOOKINGS + "/" + booking)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isOk());

        // the host still sees it, with the reason
        mvc.perform(get(OWNER + "/" + listingId).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REMOVED_BY_ADMIN"))
                .andExpect(jsonPath("$.removalReason").value("Misleading photos"));
        mvc.perform(get(OWNER).param("size", "100").header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(listingId)).value(not(empty())));

        ListingStatusHistory last = historyRepo.findByListingIdOrderByChangedAtDesc(listingId, Pageable.unpaged())
                .stream().max((a, b) -> a.getId().compareTo(b.getId())).orElseThrow();
        assertThat(last.getOldStatus()).isEqualTo(ListingStatus.APPROVED);
        assertThat(last.getNewStatus()).isEqualTo(ListingStatus.REMOVED_BY_ADMIN);
        assertThat(last.getReason()).isEqualTo("Misleading photos");
        assertThat(last.getChangedBy().getRole().name()).isEqualTo("ADMIN");
    }

    @Test
    void anyStatusExceptDeletedCanBeRemovedOnce() throws Exception {
        Long pending = support.createAccommodation(owner);
        remove(pending, "Spam").andExpect(status().isOk());

        Long paused = approved(support.createAccommodation(owner));
        mvc.perform(post(OWNER + "/" + paused + "/pause").header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isOk());
        remove(paused, "Spam").andExpect(status().isOk());

        Long rejected = support.createTour(owner);
        support.rejectListing(admin, rejected, "Too short").andExpect(status().isOk());
        remove(rejected, "Spam").andExpect(status().isOk());

        remove(rejected, "Again")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_ALREADY_REMOVED"))
                .andExpect(jsonPath("$.params.status").value("REMOVED_BY_ADMIN"));
        Long deleted = support.createAccommodation(owner);
        mvc.perform(delete(OWNER + "/" + deleted).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isNoContent());
        remove(deleted, "Spam")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.params.status").value("DELETED"));
        remove(Long.MAX_VALUE, "Spam")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
        remove(pending, " ").andExpect(status().isBadRequest());
        mvc.perform(post(ADMIN + "/" + pending + "/remove")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Mine\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void hostAddsNothingToARemovedListingButCanDeleteIt() throws Exception {
        Long cabin = approved(support.createAccommodation(owner));
        Long tour = approved(support.createTour(owner));
        remove(cabin, "Fake").andExpect(status().isOk());
        remove(tour, "Fake").andExpect(status().isOk());

        asOwner(put(OWNER + "/" + cabin + "/features").contentType(MediaType.APPLICATION_JSON)
                .content("{\"featureIds\": []}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_REMOVED"));
        asOwner(multipart(OWNER + "/" + cabin + "/images")
                .file(new MockMultipartFile("file", "image.png", "image/png", PNG)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_REMOVED"));
        asOwner(post(OWNER + "/" + cabin + "/unavailable-periods").contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\": \"%s\", \"endDate\": \"%s\"}".formatted(d, d.plusDays(1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_REMOVED"));
        asOwner(post(OWNER + "/tours/" + tour + "/dates").contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\": \"%s\", \"minGuestsDeadlineDaysBefore\": 2}".formatted(d)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_REMOVED"));
        asOwner(post(OWNER + "/accommodations/" + cabin + "/change-requests").contentType(MediaType.APPLICATION_JSON)
                .content(ListingTestSupport.accommodationJson().replace("Brvnara na Tari", "Nova brvnara")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_NOT_ALLOWED"));
        asOwner(post(OWNER + "/" + cabin + "/pause"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_PAUSABLE"));

        asOwner(delete(OWNER + "/" + cabin)).andExpect(status().isNoContent());
        assertThat(listingRepo.findById(cabin).orElseThrow().getStatus()).isEqualTo(ListingStatus.DELETED);
    }

    @Test
    void removedListingLeavesTheAdminQueues() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        Long requestId = idOf(asOwner(post(OWNER + "/accommodations/" + listingId + "/change-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ListingTestSupport.accommodationJson().replace("Brvnara na Tari", "Nova brvnara")))
                .andExpect(status().isCreated()));
        Long imageId = idOf(asOwner(multipart(OWNER + "/" + listingId + "/images")
                        .file(new MockMultipartFile("file", "image.png", "image/png", PNG)))
                .andExpect(jsonPath("$.status").value("PENDING")));

        remove(listingId, "Fake").andExpect(status().isOk());

        inQueue(ADMIN_CHANGE_REQUESTS, requestId).andExpect(jsonPath(itemPath(requestId)).value(empty()));
        inQueue(ADMIN_IMAGES, imageId).andExpect(jsonPath(itemPath(imageId)).value(empty()));
    }

    private Long approved(Long listingId) throws Exception {
        support.approveListing(admin, listingId).andExpect(status().isOk());
        return listingId;
    }

    private ResultActions remove(Long listingId, String reason) throws Exception {
        return mvc.perform(post(ADMIN + "/" + listingId + "/remove")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"%s\"}".formatted(reason)));
    }

    private ResultActions asOwner(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + owner));
    }

    private ResultActions inQueue(String path, Long id) throws Exception {
        return mvc.perform(get(path).param("size", "100").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk());
    }

    private static String itemPath(Long id) {
        return "$.content[?(@.id == %d)]".formatted(id);
    }
}
