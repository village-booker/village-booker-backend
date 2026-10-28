package com.villagebooker.storage;

// Where listing images are stored. The app keeps only the URL and the storage id in the database;
// the implementation can be swapped (Cloudinary today) without touching the services.
public interface ImageStorage {

    // throws ExternalServiceException (502) if the storage fails
    StoredImage upload(byte[] content, String folder);

    // best effort: a failure is logged, not thrown (the database row is already gone)
    void delete(String publicId);

    record StoredImage(String url, String publicId) {
    }
}
