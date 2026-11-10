package com.villagebooker.service.user;

import com.villagebooker.config.TermsProperties;
import com.villagebooker.enums.user.Role;
import com.villagebooker.model.User;
import com.villagebooker.repo.user.UserRepo;
import com.villagebooker.storage.FakeImageStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

// Two uploads of a profile picture at once: one wins, the file of the other is deleted from the storage, none leaks.
// Not @Transactional: the threads must see each other's commits, so the user is committed and removed in @AfterEach.
@SpringBootTest
// same configuration as the other tests, so they share one Spring context
@AutoConfigureMockMvc
class ProfileImageConcurrencyTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    private ProfileImageService profileImageService;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private TermsProperties terms;

    @Autowired
    private FakeImageStorage storage;

    private Long userId;

    @AfterEach
    void cleanUp() {
        if (userId != null) {
            userRepo.deleteById(userId);
        }
    }

    @Test
    void twoUploadsAtOnceLeaveOneFile() throws Exception {
        User user = new User();
        user.setEmail("test-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("not-used");
        user.setFirstName("Ana");
        user.setLastName("Anić");
        user.setRole(Role.USER);
        user.setTermsVersion(terms.version());
        user.setTermsAcceptedAt(Instant.now());
        userId = userRepo.save(user).getId();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<String>> uploads = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                uploads.add(pool.submit(() -> {
                    start.await();
                    return profileImageService.upload(userId,
                            new MockMultipartFile("file", "me.png", "image/png", PNG)).imageUrl();
                }));
            }
            start.countDown();
            List<String> urls = new ArrayList<>();
            for (Future<String> upload : uploads) {
                urls.add(upload.get());
            }

            String kept = userRepo.findById(userId).orElseThrow().getImagePublicId();
            List<String> uploaded = urls.stream()
                    .map(url -> url.substring("https://fake.storage/".length(), url.length() - ".png".length()))
                    .toList();
            assertThat(uploaded).contains(kept);
            String other = uploaded.stream().filter(id -> !id.equals(kept)).findFirst().orElseThrow();
            assertThat(storage.deleted()).contains(other).doesNotContain(kept);
        } finally {
            pool.shutdown();
        }
    }
}
