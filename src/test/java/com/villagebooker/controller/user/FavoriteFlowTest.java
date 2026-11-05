package com.villagebooker.controller.user;

import com.villagebooker.config.TermsProperties;
import com.villagebooker.controller.listing.ListingTestSupport;
import com.villagebooker.repo.user.UserRepo;
import com.villagebooker.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static com.villagebooker.controller.listing.ListingTestSupport.OWNER;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// A user's saved listings.
// Runs against the configured database; @Transactional rolls every test back, nothing stays in the DB.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FavoriteFlowTest {

    private static final String FAVORITES = "/api/users/me/favorites";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TermsProperties terms;

    private ListingTestSupport support;
    private String owner;
    private String admin;
    private String user;

    @BeforeEach
    void setUp() throws Exception {
        support = new ListingTestSupport(mvc, userRepo, jwtService, terms);
        owner = support.ownerToken();
        admin = support.adminToken();
        user = support.userToken();
    }

    @Test
    void userSavesListingsAndSeesThemAsCards() throws Exception {
        // an image added before the approval is approved with the listing, so guests see it
        Long stay = support.createAccommodation(owner);
        mvc.perform(multipart(OWNER + "/" + stay + "/images")
                        .file(new MockMultipartFile("file", "a.png", "image/png", PNG))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isCreated());
        approved(stay);
        Long tour = approved(support.createTour(owner));

        add(user, stay).andExpect(status().isNoContent());
        add(user, tour).andExpect(status().isNoContent());

        favorites(user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                // last added first
                .andExpect(jsonPath("$.content[0].listing.id").value(tour))
                .andExpect(jsonPath("$.content[0].listing.type").value("TOUR"))
                .andExpect(jsonPath("$.content[0].listing.price").value(3500.0))
                .andExpect(jsonPath("$.content[0].bookable").value(true))
                .andExpect(jsonPath("$.content[0].addedAt").exists())
                .andExpect(jsonPath("$.content[1].listing.id").value(stay))
                .andExpect(jsonPath("$.content[1].listing.type").value("ACCOMMODATION"))
                .andExpect(jsonPath("$.content[1].listing.price").value(5000.0))
                .andExpect(jsonPath("$.content[1].listing.totalPrice").doesNotExist())
                .andExpect(jsonPath("$.content[1].listing.mainImageUrl").exists());
    }

    @Test
    void addingAndRemovingTwiceChangesNothing() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));

        add(user, listingId).andExpect(status().isNoContent());
        add(user, listingId).andExpect(status().isNoContent());
        favorites(user).andExpect(jsonPath("$.totalElements").value(1));

        remove(user, listingId).andExpect(status().isNoContent());
        remove(user, listingId).andExpect(status().isNoContent());
        favorites(user).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void everyUserHasOwnList() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        add(user, listingId).andExpect(status().isNoContent());
        String other = support.userToken();

        favorites(other).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(FAVORITES + "/ids?listingIds=" + listingId).header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
                .andExpect(jsonPath("$", hasSize(0)));
        // removing from another list does not touch this one
        remove(other, listingId).andExpect(status().isNoContent());
        favorites(user).andExpect(jsonPath("$.totalElements").value(1));
        // a host and an admin have a list too
        add(owner, listingId).andExpect(status().isNoContent());
        add(admin, listingId).andExpect(status().isNoContent());
        favorites(owner).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void onlyAListingGuestsCanOpenCanBeSaved() throws Exception {
        Long pending = support.createAccommodation(owner);
        Long rejected = support.createAccommodation(owner);
        support.rejectListing(admin, rejected, "Nepotpun opis").andExpect(status().isOk());

        add(user, pending)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
        add(user, rejected).andExpect(status().isNotFound());
        add(user, 999_999_999L).andExpect(status().isNotFound());
        favorites(user).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void pausedListingStaysInTheListButIsNotBookable() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        add(user, listingId).andExpect(status().isNoContent());

        mvc.perform(post(OWNER + "/" + listingId + "/pause").header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isOk());

        favorites(user)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].bookable").value(false));
        // a paused listing can still be saved, its page is open
        String other = support.userToken();
        add(other, listingId).andExpect(status().isNoContent());
    }

    @Test
    void deletedListingDropsOutOfTheList() throws Exception {
        Long kept = approved(support.createAccommodation(owner));
        Long deleted = approved(support.createAccommodation(owner));
        add(user, kept).andExpect(status().isNoContent());
        add(user, deleted).andExpect(status().isNoContent());

        mvc.perform(delete(OWNER + "/" + deleted).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isNoContent());

        favorites(user)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].listing.id").value(kept));
        mvc.perform(get(FAVORITES + "/ids?listingIds=" + kept + "," + deleted)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void savedIdsAmongAPageOfCards() throws Exception {
        Long a = approved(support.createAccommodation(owner));
        Long b = approved(support.createAccommodation(owner));
        Long c = approved(support.createAccommodation(owner));
        add(user, a).andExpect(status().isNoContent());
        add(user, c).andExpect(status().isNoContent());

        mvc.perform(get(FAVORITES + "/ids?listingIds=" + a + "," + b + "," + c)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", containsInAnyOrder(a.intValue(), c.intValue())));
        mvc.perform(get(FAVORITES + "/ids?listingIds=" + b).header(HttpHeaders.AUTHORIZATION, "Bearer " + user))
                .andExpect(jsonPath("$", hasSize(0)));

        String tooMany = LongStream.rangeClosed(1, 101).mapToObj(Long::toString).collect(Collectors.joining(","));
        mvc.perform(get(FAVORITES + "/ids?listingIds=" + tooMany).header(HttpHeaders.AUTHORIZATION, "Bearer " + user))
                .andExpect(status().isBadRequest());
    }

    @Test
    void favoritesNeedALogin() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));

        mvc.perform(get(FAVORITES)).andExpect(status().isUnauthorized());
        mvc.perform(put(FAVORITES + "/" + listingId)).andExpect(status().isUnauthorized());
    }

    private Long approved(Long listingId) throws Exception {
        support.approveListing(admin, listingId).andExpect(status().isOk());
        return listingId;
    }

    private ResultActions add(String token, Long listingId) throws Exception {
        return mvc.perform(put(FAVORITES + "/" + listingId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions remove(String token, Long listingId) throws Exception {
        return mvc.perform(delete(FAVORITES + "/" + listingId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions favorites(String token) throws Exception {
        return mvc.perform(get(FAVORITES).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
