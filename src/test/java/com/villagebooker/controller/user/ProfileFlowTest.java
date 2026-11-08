package com.villagebooker.controller.user;

import com.jayway.jsonpath.JsonPath;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.controller.listing.ListingTestSupport;
import com.villagebooker.enums.user.Role;
import com.villagebooker.model.User;
import com.villagebooker.repo.user.UserRepo;
import com.villagebooker.security.JwtService;
import com.villagebooker.storage.FakeImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The user edits their profile, password, payout account (host) and profile picture; the storage is FakeImageStorage.
// Runs against the configured database; @Transactional rolls every test back, nothing stays in the DB.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileFlowTest {

    private static final String ME = "/api/users/me";
    private static final String PASSWORD = "secret-pass-123";
    private static final String ACCOUNT = "160-5100000123456-65";
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
    private String user;

    @BeforeEach
    void setUp() throws Exception {
        support = new ListingTestSupport(mvc, userRepo, jwtService, terms);
        user = support.userToken();
    }

    @Test
    void userEditsNameAndPhone() throws Exception {
        String email = email(user);

        updateProfile(user, "{\"firstName\": \"  Marko \", \"lastName\": \" Marković\", \"phone\": \" 064 123 4567 \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Marko"))
                .andExpect(jsonPath("$.lastName").value("Marković"))
                .andExpect(jsonPath("$.phone").value("064 123 4567"))
                .andExpect(jsonPath("$.email").value(email));
        me(user).andExpect(jsonPath("$.firstName").value("Marko"));

        // a blank phone removes it
        updateProfile(user, "{\"firstName\": \"Marko\", \"lastName\": \"Marković\", \"phone\": \"  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(nullValue()));
    }

    @Test
    void profileIsValidated() throws Exception {
        updateProfile(user, "{\"firstName\": \" \", \"lastName\": \"Marković\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName.code").value("NotBlank"));
        updateProfile(user, "{\"firstName\": \"Marko\", \"lastName\": \"%s\"}".formatted("a".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.lastName.code").value("Size"));
        updateProfile(user, "{\"firstName\": \"Marko\", \"lastName\": \"M\", \"phone\": \"%s\"}".formatted("1".repeat(31)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phone.code").value("Size"));
        me(user).andExpect(jsonPath("$.firstName").value("Ana"));
    }

    @Test
    void passwordChangesWithTheCurrentPassword() throws Exception {
        String email = email(user);

        changePassword(user, PASSWORD, "new-secret-456").andExpect(status().isNoContent());

        login(email, "new-secret-456").andExpect(status().isOk());
        login(email, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongCurrentPasswordChangesNothing() throws Exception {
        String email = email(user);

        changePassword(user, "not-my-password", "new-secret-456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));

        login(email, PASSWORD).andExpect(status().isOk());
        login(email, "new-secret-456").andExpect(status().isUnauthorized());
    }

    @Test
    void newPasswordIsValidated() throws Exception {
        changePassword(user, PASSWORD, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword.code").value("Size"));
        // 37 characters, but 74 bytes: more than BCrypt uses
        changePassword(user, PASSWORD, "č".repeat(37))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_LONG"));
        login(email(user), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void hostChangesPayoutAccountWithThePassword() throws Exception {
        String host = support.ownerToken();
        me(host).andExpect(jsonPath("$.bankAccount").value(ACCOUNT));

        // a shortened middle part is accepted and stored in full
        updateBankAccount(host, "205-9011003562-09", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankAccount").value("205-0009011003562-09"));
        me(host).andExpect(jsonPath("$.bankAccount").value("205-0009011003562-09"));
    }

    @Test
    void payoutAccountNeedsTheRightPasswordAndAValidNumber() throws Exception {
        String host = support.ownerToken();

        updateBankAccount(host, "205-9011003562-09", "not-my-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));
        updateBankAccount(host, "205-9011003562-08", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BANK_ACCOUNT"));
        me(host).andExpect(jsonPath("$.bankAccount").value(ACCOUNT));
    }

    @Test
    void onlyAHostHasAPayoutAccount() throws Exception {
        // stopped by the role in the token already, before the service
        updateBankAccount(user, ACCOUNT, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        me(user).andExpect(jsonPath("$.bankAccount").value(nullValue()));

        // the role is read from the database, not from the token
        User guest = userRepo.findById(userId(user)).orElseThrow();
        guest.setRole(Role.OWNER);
        String forged = jwtService.issue(guest).token();
        guest.setRole(Role.USER);
        userRepo.saveAndFlush(guest);
        updateBankAccount(forged, ACCOUNT, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_A_HOST"));
    }

    @Test
    void newProfilePictureReplacesTheOldFile() throws Exception {
        String first = JsonPath.read(uploadImage(user, PNG)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(startsWith("https://fake.storage/village-booker/users/")))
                .andReturn().getResponse().getContentAsString(), "$.imageUrl");
        me(user).andExpect(jsonPath("$.imageUrl").value(first));
        assertThat(storage.deleted()).noneMatch(first::contains);

        String second = JsonPath.read(uploadImage(user, PNG).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.imageUrl");

        assertThat(second).isNotEqualTo(first);
        assertThat(storage.deleted()).anyMatch(first::contains).noneMatch(second::contains);
    }

    @Test
    void userRemovesProfilePicture() throws Exception {
        String url = JsonPath.read(uploadImage(user, PNG).andReturn().getResponse().getContentAsString(), "$.imageUrl");

        mvc.perform(delete(ME + "/image").header(HttpHeaders.AUTHORIZATION, "Bearer " + user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(nullValue()));

        assertThat(storage.deleted()).anyMatch(url::contains);
        me(user).andExpect(jsonPath("$.imageUrl").value(nullValue()));
        // nothing left to remove
        int deleted = storage.deleted().size();
        mvc.perform(delete(ME + "/image").header(HttpHeaders.AUTHORIZATION, "Bearer " + user))
                .andExpect(status().isOk());
        assertThat(storage.deleted()).hasSize(deleted);
    }

    @Test
    void onlyImagesAreAccepted() throws Exception {
        uploadImage(user, "not an image".getBytes())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_INVALID_TYPE"));
        uploadImage(user, new byte[0])
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_INVALID_TYPE"));
        me(user).andExpect(jsonPath("$.imageUrl").value(nullValue()));
    }

    @Test
    void hostPictureIsShownOnTheListingPage() throws Exception {
        String host = support.ownerToken();
        Long listingId = support.createAccommodation(host);
        support.approveListing(support.adminToken(), listingId).andExpect(status().isOk());
        String url = JsonPath.read(uploadImage(host, PNG).andReturn().getResponse().getContentAsString(), "$.imageUrl");

        mvc.perform(get("/api/listings/" + listingId))
                .andExpect(jsonPath("$.host.imageUrl").value(url));
    }

    @Test
    void adminRemovesAnInappropriatePicture() throws Exception {
        String url = JsonPath.read(uploadImage(user, PNG).andReturn().getResponse().getContentAsString(), "$.imageUrl");
        String admin = support.adminToken();

        mvc.perform(delete("/api/admin/users/" + userId(user) + "/image")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isNoContent());

        me(user).andExpect(jsonPath("$.imageUrl").value(nullValue()));
        assertThat(storage.deleted()).anyMatch(url::contains);
        mvc.perform(delete("/api/admin/users/999999999/image").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void onlyAnAdminRemovesOtherUsersPictures() throws Exception {
        uploadImage(user, PNG).andExpect(status().isOk());

        mvc.perform(delete("/api/admin/users/" + userId(user) + "/image")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + support.ownerToken()))
                .andExpect(status().isForbidden());
        me(user).andExpect(jsonPath("$.imageUrl").exists());
    }

    @Test
    void profileNeedsALogin() throws Exception {
        mvc.perform(put(ME).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\": \"Marko\", \"lastName\": \"Marković\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(ME + "/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"x\", \"newPassword\": \"new-secret-456\"}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions me(String token) throws Exception {
        return mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String email(String token) throws Exception {
        return JsonPath.read(me(token).andReturn().getResponse().getContentAsString(), "$.email");
    }

    private Long userId(String token) throws Exception {
        return ((Number) JsonPath.read(me(token).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private ResultActions updateProfile(String token, String json) throws Exception {
        return mvc.perform(put(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions changePassword(String token, String current, String newPassword) throws Exception {
        return mvc.perform(put(ME + "/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}".formatted(current, newPassword)));
    }

    private ResultActions updateBankAccount(String token, String account, String password) throws Exception {
        return mvc.perform(put(ME + "/bank-account").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bankAccount\": \"%s\", \"currentPassword\": \"%s\"}".formatted(account, password)));
    }

    private ResultActions uploadImage(String token, byte[] content) throws Exception {
        return mvc.perform(multipart(HttpMethod.PUT, ME + "/image")
                .file(new MockMultipartFile("file", "me.png", "image/png", content))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }
}
