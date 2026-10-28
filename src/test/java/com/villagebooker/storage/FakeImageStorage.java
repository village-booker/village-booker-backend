package com.villagebooker.storage;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

// Replaces Cloudinary in every test (@Primary, picked up by component scan of the test sources):
// no network, no credits used; remembers what was uploaded and deleted for the assertions.
@Primary
@Component
public class FakeImageStorage implements ImageStorage {

    private final AtomicInteger counter = new AtomicInteger();
    private final List<String> deleted = Collections.synchronizedList(new ArrayList<>());

    @Override
    public StoredImage upload(byte[] content, String folder) {
        String publicId = folder + "/img-" + counter.incrementAndGet();
        return new StoredImage("https://fake.storage/" + publicId + ".png", publicId);
    }

    @Override
    public void delete(String publicId) {
        deleted.add(publicId);
    }

    public List<String> deleted() {
        return deleted;
    }
}
