package com.villagebooker.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.common.exception.ExternalServiceException;
import com.villagebooker.config.CloudinaryProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

// ImageStorage on Cloudinary. Called outside database transactions (an upload takes seconds).
@Slf4j
@Component
public class CloudinaryImageStorage implements ImageStorage {

    private final Cloudinary cloudinary;

    public CloudinaryImageStorage(CloudinaryProperties properties) {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", properties.cloudName(),
                "api_key", properties.apiKey(),
                "api_secret", properties.apiSecret(),
                "secure", true));
    }

    @Override
    public StoredImage upload(byte[] content, String folder) {
        try {
            Map<?, ?> result = cloudinary.uploader().upload(content, ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", "image"));
            return new StoredImage((String) result.get("secure_url"), (String) result.get("public_id"));
        } catch (Exception e) {
            log.error("Image upload to Cloudinary failed (folder {})", folder, e);
            throw new ExternalServiceException(ErrorCode.IMAGE_UPLOAD_FAILED, "Image upload failed. Try again later.");
        }
    }

    @Override
    public void delete(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("invalidate", true));
        } catch (Exception e) {
            log.warn("Could not delete image {} from Cloudinary; it stays there as an orphan", publicId, e);
        }
    }
}
