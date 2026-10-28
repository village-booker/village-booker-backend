package com.villagebooker.controller.listing;

import com.villagebooker.config.TermsProperties;
import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.model.ListingStatusHistory;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.villagebooker.controller.listing.ListingTestSupport.ADMIN;
import static com.villagebooker.controller.listing.ListingTestSupport.OWNER;
import static com.villagebooker.controller.listing.ListingTestSupport.accommodationJson;
import static com.villagebooker.controller.listing.ListingTestSupport.tourJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Fixing a REJECTED listing goes through a change request (ChangeRequestFlowTest).
// Runs against the configured database; @Transactional rolls every test back, nothing stays in the DB.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ListingModerationFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private ListingStatusHistoryRepo historyRepo;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TermsProperties terms;

    private ListingTestSupport support;

    @BeforeEach
    void setUp() {
        support = new ListingTestSupport(mvc, userRepo, jwtService, terms);
    }

    @Test
    void adminSeesPendingQueueAndListingWithItsOwner() throws Exception {
        Long id = support.createTour(support.ownerToken());
        String admin = support.adminToken();

        mvc.perform(get(ADMIN).param("size", "1").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));

        mvc.perform(get(ADMIN + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listing.id").value(id))
                .andExpect(jsonPath("$.listing.status").value("PENDING"))
                .andExpect(jsonPath("$.owner.email").isNotEmpty());
    }

    @Test
    void adminApprovesPendingListingOnlyOnceAndItIsRecorded() throws Exception {
        Long id = support.createTour(support.ownerToken());
        String admin = support.adminToken();

        support.approveListing(admin, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listing.status").value("APPROVED"));

        support.approveListing(admin, id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_PENDING"))
                .andExpect(jsonPath("$.params.status").value("APPROVED"));

        List<ListingStatusHistory> history = history(id);
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getOldStatus()).isEqualTo(ListingStatus.PENDING);
        assertThat(history.getFirst().getNewStatus()).isEqualTo(ListingStatus.APPROVED);
        assertThat(history.getFirst().getChangedBy()).isNotNull();
    }

    @Test
    void rejectNeedsAReasonAndTheOwnerSeesIt() throws Exception {
        String owner = support.ownerToken();
        Long id = support.createTour(owner);
        String admin = support.adminToken();

        support.rejectListing(admin, id, "  ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason.code").value("NotBlank"));

        support.rejectListing(admin, id, "Dodajte opis ture.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listing.status").value("REJECTED"))
                .andExpect(jsonPath("$.listing.rejectionReason").value("Dodajte opis ture."));

        mvc.perform(get(OWNER + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Dodajte opis ture."));

        support.approveListing(admin, id).andExpect(status().isConflict());

        assertThat(history(id).getFirst().getReason()).isEqualTo("Dodajte opis ture.");
    }

    @Test
    void pendingListingIsEditedDirectlyAndStaysPending() throws Exception {
        String owner = support.ownerToken();
        Long id = support.createTour(owner);

        updateTour(owner, id, tourJson().replace("\"pricePerPerson\": 3500", "\"pricePerPerson\": 4000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.tour.pricePerPerson").value(4000));

        assertThat(history(id)).isEmpty();
    }

    @Test
    void rejectedOrApprovedListingIsNotEditedDirectly() throws Exception {
        String owner = support.ownerToken();
        String admin = support.adminToken();
        Long rejected = support.createTour(owner);
        Long approved = support.createTour(owner);
        support.rejectListing(admin, rejected, "Dodajte opis ture.").andExpect(status().isOk());
        support.approveListing(admin, approved).andExpect(status().isOk());

        updateTour(owner, rejected, tourJson())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_EDITABLE"))
                .andExpect(jsonPath("$.params.status").value("REJECTED"));
        updateTour(owner, approved, tourJson())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.params.status").value("APPROVED"));
    }

    @Test
    void ownerCannotEditSomeoneElsesListingOrUseTheWrongType() throws Exception {
        String owner = support.ownerToken();
        Long id = support.createTour(owner);

        updateTour(support.ownerToken(), id, tourJson())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_LISTING_OWNER"));

        mvc.perform(put(OWNER + "/accommodations/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accommodationJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LISTING_TYPE_MISMATCH"));
    }

    @Test
    void onlyAdminModerates() throws Exception {
        String owner = support.ownerToken();
        Long id = support.createTour(owner);

        support.approveListing(owner, id)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get(ADMIN).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownListingReturns404() throws Exception {
        support.approveListing(support.adminToken(), 999_999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
    }

    private ResultActions updateTour(String token, Long id, String json) throws Exception {
        return mvc.perform(put(OWNER + "/tours/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private List<ListingStatusHistory> history(Long listingId) {
        return historyRepo.findByListingIdOrderByChangedAtDesc(listingId, Pageable.unpaged()).getContent();
    }
}
