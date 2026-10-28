package com.villagebooker.controller.listing;

import com.jayway.jsonpath.JsonPath;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.repo.user.UserRepo;
import com.villagebooker.security.JwtService;
import com.villagebooker.storage.FakeImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static com.villagebooker.controller.listing.ListingTestSupport.ADMIN;
import static com.villagebooker.controller.listing.ListingTestSupport.ADMIN_IMAGES;
import static com.villagebooker.controller.listing.ListingTestSupport.OWNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Listing images; the storage is FakeImageStorage (no Cloudinary calls in tests).
// Runs against the configured database; @Transactional rolls every test back, nothing stays in the DB.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ListingImageFlowTest {

    // the first bytes of a PNG file are enough for the type check
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TermsProperties terms;

    @Autowired
    private FakeImageStorage storage;

    private ListingTestSupport support;

    @BeforeEach
    void setUp() {
        support = new ListingTestSupport(mvc, userRepo, jwtService, terms);
    }

    @Test
    void ownerUploadsImagesInOrder() throws Exception {
        String owner = support.ownerToken();
        Long listingId = support.createAccommodation(owner);

        upload(owner, listingId, png())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(0))
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("https://fake.storage/")));
        upload(owner, listingId, png())
                .andExpect(jsonPath("$.sortOrder").value(1));

        mvc.perform(get(OWNER + "/" + listingId).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.images[0].sortOrder").value(0))
                .andExpect(jsonPath("$.images[1].sortOrder").value(1));
    }

    @Test
    void imageOfANotYetPublicListingIsApprovedWithTheListing() throws Exception {
        String owner = support.ownerToken();
        Long listingId = support.createAccommodation(owner);

        upload(owner, listingId, png())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void imageAddedToAPublicListingWaitsForTheAdmin() throws Exception {
        String owner = support.ownerToken();
        String admin = support.adminToken();
        Long listingId = support.createAccommodation(owner);
        support.approveListing(admin, listingId).andExpect(status().isOk());

        Long imageId = idOf(upload(owner, listingId, png())
                .andExpect(jsonPath("$.status").value("PENDING")));

        mvc.perform(get(ADMIN_IMAGES).param("size", "100").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)].listingId".formatted(imageId)).value(
                        org.hamcrest.Matchers.hasItem(listingId.intValue())));

        mvc.perform(post(ADMIN_IMAGES + "/" + imageId + "/approve").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(post(ADMIN_IMAGES + "/" + imageId + "/approve").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMAGE_NOT_PENDING"));
    }

    @Test
    void rejectedImageIsRemovedFromStorageAndTheOwnerSeesWhy() throws Exception {
        String owner = support.ownerToken();
        String admin = support.adminToken();
        Long listingId = support.createAccommodation(owner);
        Long approvedId = idOf(upload(owner, listingId, png()));
        support.approveListing(admin, listingId).andExpect(status().isOk());
        String pendingBody = bodyOf(upload(owner, listingId, png()));
        Long pendingId = idOf(pendingBody);

        mvc.perform(post(ADMIN_IMAGES + "/" + pendingId + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Slika nije sa ovog imanja.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(storage.deleted()).anyMatch(pendingBody::contains);

        mvc.perform(get(OWNER + "/" + listingId).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(jsonPath("$.images[1].id").value(pendingId))
                .andExpect(jsonPath("$.images[1].status").value("REJECTED"))
                .andExpect(jsonPath("$.images[1].rejectionReason").value("Slika nije sa ovog imanja."));

        // a rejected image is not part of the order and the owner can remove it from the list
        reorder(owner, listingId, "[%d]".formatted(approvedId)).andExpect(status().isOk());
        mvc.perform(delete(OWNER + "/" + listingId + "/images/" + pendingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isNoContent());
    }

    @Test
    void onlyAdminReviewsImages() throws Exception {
        String owner = support.ownerToken();
        mvc.perform(get(ADMIN_IMAGES).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void onlyRealImagesAreAccepted() throws Exception {
        String owner = support.ownerToken();
        Long listingId = support.createAccommodation(owner);

        // claims to be a PNG, but it is text
        upload(owner, listingId, new MockMultipartFile("file", "a.png", "image/png", "not an image".getBytes()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_INVALID_TYPE"));
    }

    @Test
    void atMost15ImagesPerListing() throws Exception {
        String owner = support.ownerToken();
        Long listingId = support.createAccommodation(owner);
        for (int i = 0; i < 15; i++) {
            upload(owner, listingId, png()).andExpect(status().isCreated());
        }

        upload(owner, listingId, png())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_LIMIT_REACHED"))
                .andExpect(jsonPath("$.params.max").value(15));
    }

    @Test
    void reorderNeedsEveryImageExactlyOnce() throws Exception {
        String owner = support.ownerToken();
        Long listingId = support.createAccommodation(owner);
        Long first = idOf(upload(owner, listingId, png()));
        Long second = idOf(upload(owner, listingId, png()));

        reorder(owner, listingId, "[%d, %d]".formatted(second, first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[0].sortOrder").value(0))
                .andExpect(jsonPath("$[1].id").value(first));

        reorder(owner, listingId, "[%d]".formatted(first))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_ORDER_INVALID"));
        reorder(owner, listingId, "[%d, %d]".formatted(first, first))
                .andExpect(jsonPath("$.code").value("IMAGE_ORDER_INVALID"));
    }

    @Test
    void deletingAnImageRenumbersTheRestAndRemovesTheFile() throws Exception {
        String owner = support.ownerToken();
        Long listingId = support.createAccommodation(owner);
        String firstBody = bodyOf(upload(owner, listingId, png()));
        Long first = idOf(firstBody);
        upload(owner, listingId, png());

        mvc.perform(delete(OWNER + "/" + listingId + "/images/" + first)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isNoContent());

        mvc.perform(get(OWNER + "/" + listingId).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(jsonPath("$.images.length()").value(1))
                .andExpect(jsonPath("$.images[0].sortOrder").value(0));
        assertThat(storage.deleted()).anyMatch(firstBody::contains);
    }

    @Test
    void adminRemovesAnImageButOtherOwnersCannot() throws Exception {
        String owner = support.ownerToken();
        Long listingId = support.createAccommodation(owner);
        Long imageId = idOf(upload(owner, listingId, png()));

        mvc.perform(delete(OWNER + "/" + listingId + "/images/" + imageId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + support.ownerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_LISTING_OWNER"));

        mvc.perform(delete(ADMIN + "/" + listingId + "/images/" + imageId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + support.adminToken()))
                .andExpect(status().isNoContent());
        mvc.perform(delete(ADMIN + "/" + listingId + "/images/" + imageId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + support.adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMAGE_NOT_FOUND"));
    }

    private ResultActions upload(String token, Long listingId, MockMultipartFile file) throws Exception {
        return mvc.perform(multipart(OWNER + "/" + listingId + "/images")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions reorder(String token, Long listingId, String idsJson) throws Exception {
        return mvc.perform(put(OWNER + "/" + listingId + "/images/order")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imageIds\": " + idsJson + "}"));
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("file", "image.png", "image/png", PNG);
    }

    private static String bodyOf(ResultActions result) throws Exception {
        return result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private static Long idOf(ResultActions result) throws Exception {
        return idOf(bodyOf(result));
    }

    private static Long idOf(String body) {
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
