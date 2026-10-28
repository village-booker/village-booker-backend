package com.villagebooker.controller.feature;

import com.villagebooker.controller.listing.ListingTestSupport;

import com.jayway.jsonpath.JsonPath;
import com.villagebooker.config.TermsProperties;
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

import java.util.UUID;

import static com.villagebooker.controller.listing.ListingTestSupport.OWNER;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs against the configured database; @Transactional rolls every test back, nothing stays in the DB.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FeatureFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepo userRepo;

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
    void adminCreatesFeatureAndEveryoneCanReadTheCatalog() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        createFeature(support.adminToken(), "Bazen " + suffix, "Pool " + suffix)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nameSr").value("Bazen " + suffix))
                .andExpect(jsonPath("$.nameEn").value("Pool " + suffix));

        // public, no token
        mvc.perform(get("/api/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nameEn", hasItem("Pool " + suffix)));
    }

    @Test
    void featureNamesAreUniqueIgnoringCase() throws Exception {
        String admin = support.adminToken();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        createFeature(admin, "Parking " + suffix, "Parking lot " + suffix).andExpect(status().isCreated());

        createFeature(admin, "PARKING " + suffix, "Something else " + suffix)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEATURE_NAME_TAKEN"));
    }

    @Test
    void onlyAdminChangesTheCatalog() throws Exception {
        createFeature(support.ownerToken(), "Sauna", "Sauna")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void ownerSetsFeaturesOfAListingAndAFeatureInUseCannotBeDeleted() throws Exception {
        String admin = support.adminToken();
        String owner = support.ownerToken();
        Long wifi = idOf(createFeature(admin, "Wi-Fi " + UUID.randomUUID(), "Wi-Fi " + UUID.randomUUID()));
        Long grill = idOf(createFeature(admin, "Roštilj " + UUID.randomUUID(), "Barbecue " + UUID.randomUUID()));
        Long listingId = support.createAccommodation(owner);

        setFeatures(owner, listingId, "[%d, %d, %d]".formatted(wifi, grill, wifi))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()").value(2));

        mvc.perform(delete("/api/admin/features/" + wifi).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEATURE_IN_USE"));

        setFeatures(owner, listingId, "[%d]".formatted(grill))
                .andExpect(jsonPath("$.features.length()").value(1));
        mvc.perform(delete("/api/admin/features/" + wifi).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void unknownFeatureIdReturns400() throws Exception {
        String owner = support.ownerToken();
        Long listingId = support.createAccommodation(owner);

        setFeatures(owner, listingId, "[999999999]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FEATURE_NOT_FOUND"))
                .andExpect(jsonPath("$.params.featureIds[0]").value(999999999));
    }

    @Test
    void ownerCannotSetFeaturesOfSomeoneElsesListing() throws Exception {
        Long listingId = support.createAccommodation(support.ownerToken());

        setFeatures(support.ownerToken(), listingId, "[]")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_LISTING_OWNER"));
    }

    private ResultActions createFeature(String token, String nameSr, String nameEn) throws Exception {
        return mvc.perform(post("/api/admin/features")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nameSr\": \"%s\", \"nameEn\": \"%s\"}".formatted(nameSr, nameEn)));
    }

    private ResultActions setFeatures(String token, Long listingId, String idsJson) throws Exception {
        return mvc.perform(put(OWNER + "/" + listingId + "/features")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"featureIds\": " + idsJson + "}"));
    }

    private static Long idOf(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
