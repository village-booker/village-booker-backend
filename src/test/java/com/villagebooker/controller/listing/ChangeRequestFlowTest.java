package com.villagebooker.controller.listing;

import com.jayway.jsonpath.JsonPath;
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

import static com.villagebooker.controller.listing.ListingTestSupport.ADMIN_CHANGE_REQUESTS;
import static com.villagebooker.controller.listing.ListingTestSupport.OWNER;
import static com.villagebooker.controller.listing.ListingTestSupport.accommodationJson;
import static com.villagebooker.controller.listing.ListingTestSupport.tourJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs against the configured database; @Transactional rolls every test back, nothing stays in the DB.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChangeRequestFlowTest {

    // tourJson() with a new price and a smaller group
    private static final String CHANGED_TOUR = tourJson()
            .replace("\"pricePerPerson\": 3500", "\"pricePerPerson\": 4000")
            .replace("\"maxGuests\": 12", "\"maxGuests\": 10");

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
    void ownerRequestsOnlyChangedFieldsAndListingStaysAsItWas() throws Exception {
        String owner = support.ownerToken();
        String admin = support.adminToken();
        Long listingId = approvedTour(owner, admin);

        Long requestId = idOf(requestTourChange(owner, listingId, CHANGED_TOUR)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.listingId").value(listingId))
                .andExpect(jsonPath("$.requestedChanges.name").doesNotExist())
                .andExpect(jsonPath("$.requestedChanges.durationDays").doesNotExist())
                .andExpect(jsonPath("$.requestedChanges.pricePerPerson").value("4000"))
                .andExpect(jsonPath("$.requestedChanges.maxGuests").value("10"))
                .andExpect(jsonPath("$.oldValues.pricePerPerson").value("3500"))
                .andExpect(jsonPath("$.oldValues.maxGuests").value("12")));

        mvc.perform(get(OWNER + "/" + listingId).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(jsonPath("$.tour.pricePerPerson").value(3500))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(get(ADMIN_CHANGE_REQUESTS + "/" + requestId).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requester.email").isNotEmpty());
    }

    @Test
    void adminApprovesAndChangesAreAppliedWithPreviousValues() throws Exception {
        String owner = support.ownerToken();
        String admin = support.adminToken();
        Long listingId = approvedTour(owner, admin);
        Long requestId = idOf(requestTourChange(owner, listingId, CHANGED_TOUR));

        approve(admin, requestId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.processedAt").isNotEmpty())
                .andExpect(jsonPath("$.oldValues.pricePerPerson").value("3500"));

        mvc.perform(get(OWNER + "/" + listingId).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(jsonPath("$.tour.pricePerPerson").value(4000))
                .andExpect(jsonPath("$.maxGuests").value(10))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        approve(admin, requestId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_NOT_PENDING"));

        // the listing was APPROVED before and after: no status change, nothing in the status history but the first approval
        assertThat(history(listingId)).hasSize(1);
    }

    @Test
    void rejectedRequestLeavesTheListingUnchangedAndTheOwnerSeesWhy() throws Exception {
        String owner = support.ownerToken();
        String admin = support.adminToken();
        Long listingId = approvedTour(owner, admin);
        Long requestId = idOf(requestTourChange(owner, listingId, CHANGED_TOUR));

        reject(admin, requestId, "Cena ne može da raste usred sezone.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.oldValues").doesNotExist());

        mvc.perform(get(OWNER + "/" + listingId + "/change-requests").header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].rejectionReason").value("Cena ne može da raste usred sezone."));
        mvc.perform(get(OWNER + "/" + listingId).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(jsonPath("$.tour.pricePerPerson").value(3500));

        // after a rejection a new request may be sent
        requestTourChange(owner, listingId, CHANGED_TOUR).andExpect(status().isCreated());
    }

    @Test
    void fixedRejectedListingIsApprovedTogetherWithTheRequest() throws Exception {
        String owner = support.ownerToken();
        String admin = support.adminToken();
        Long listingId = support.createTour(owner);
        support.rejectListing(admin, listingId, "Dodajte opis ture.").andExpect(status().isOk());

        String fixed = tourJson().replace("\"name\": \"Rafting Drinom\"",
                "\"name\": \"Rafting Drinom\", \"description\": \"Ceo dan na reci, ručak uključen\"");
        Long requestId = idOf(requestTourChange(owner, listingId, fixed)
                .andExpect(jsonPath("$.requestedChanges.description").value("Ceo dan na reci, ručak uključen"))
                .andExpect(jsonPath("$.oldValues.description").doesNotExist()));

        approve(admin, requestId).andExpect(status().isOk());

        mvc.perform(get(OWNER + "/" + listingId).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist())
                .andExpect(jsonPath("$.description").value("Ceo dan na reci, ručak uključen"));

        List<ListingStatusHistory> history = history(listingId);
        assertThat(history).extracting(ListingStatusHistory::getNewStatus)
                .containsExactlyInAnyOrder(ListingStatus.REJECTED, ListingStatus.APPROVED);
        assertThat(history).allSatisfy(h -> assertThat(h.getChangedBy()).isNotNull());
    }

    @Test
    void onlyOnePendingRequestPerListing() throws Exception {
        String owner = support.ownerToken();
        Long listingId = approvedTour(owner, support.adminToken());
        requestTourChange(owner, listingId, CHANGED_TOUR).andExpect(status().isCreated());

        requestTourChange(owner, listingId, CHANGED_TOUR.replace("\"durationDays\": 2", "\"durationDays\": 3"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_ALREADY_PENDING"));
    }

    @Test
    void requestWithoutChangesReturns400() throws Exception {
        String owner = support.ownerToken();
        Long listingId = approvedTour(owner, support.adminToken());

        requestTourChange(owner, listingId, tourJson())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_CHANGES"));
    }

    @Test
    void pendingListingIsEditedDirectlyNotThroughARequest() throws Exception {
        String owner = support.ownerToken();
        Long listingId = support.createTour(owner);

        requestTourChange(owner, listingId, CHANGED_TOUR)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_NOT_ALLOWED"))
                .andExpect(jsonPath("$.params.status").value("PENDING"));
    }

    @Test
    void businessRulesApplyToRequestsToo() throws Exception {
        String owner = support.ownerToken();
        Long listingId = approvedTour(owner, support.adminToken());

        requestTourChange(owner, listingId, tourJson().replace("\"NON_REFUNDABLE\"", "\"PARTIAL\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REFUND_PERCENTAGE_REQUIRED"));
    }

    @Test
    void ownerCannotRequestChangesOfSomeoneElsesListingOrWithTheWrongType() throws Exception {
        String owner = support.ownerToken();
        Long listingId = approvedTour(owner, support.adminToken());

        requestTourChange(support.ownerToken(), listingId, CHANGED_TOUR)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_LISTING_OWNER"));

        mvc.perform(post(OWNER + "/accommodations/" + listingId + "/change-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accommodationJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LISTING_TYPE_MISMATCH"));
    }

    @Test
    void onlyAdminDecidesOnRequests() throws Exception {
        String owner = support.ownerToken();
        Long listingId = approvedTour(owner, support.adminToken());
        Long requestId = idOf(requestTourChange(owner, listingId, CHANGED_TOUR));

        approve(owner, requestId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        approve(support.adminToken(), 999_999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_NOT_FOUND"));
    }

    private Long approvedTour(String owner, String admin) throws Exception {
        Long id = support.createTour(owner);
        support.approveListing(admin, id).andExpect(status().isOk());
        return id;
    }

    private ResultActions requestTourChange(String token, Long listingId, String json) throws Exception {
        return mvc.perform(post(OWNER + "/tours/" + listingId + "/change-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions approve(String token, Long requestId) throws Exception {
        return mvc.perform(post(ADMIN_CHANGE_REQUESTS + "/" + requestId + "/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions reject(String token, Long requestId, String reason) throws Exception {
        return mvc.perform(post(ADMIN_CHANGE_REQUESTS + "/" + requestId + "/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"%s\"}".formatted(reason)));
    }

    private static Long idOf(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private List<ListingStatusHistory> history(Long listingId) {
        return historyRepo.findByListingIdOrderByChangedAtDesc(listingId, Pageable.unpaged()).getContent();
    }
}
