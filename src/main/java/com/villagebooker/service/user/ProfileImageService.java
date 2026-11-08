package com.villagebooker.service.user;

import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.common.exception.NotFoundException;
import com.villagebooker.dto.user.UserResponse;
import com.villagebooker.model.User;
import com.villagebooker.repo.user.UserRepo;
import com.villagebooker.storage.ImageFiles;
import com.villagebooker.storage.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

// Profile picture: shown at once, no admin review; the admin can remove one. As with listing images the upload runs
// outside a transaction (it takes seconds), the row is written in a short one, and the old file is deleted from the
// storage only after the row no longer points to it.
@Service
@RequiredArgsConstructor
public class ProfileImageService {

    private final UserRepo userRepo;
    private final ImageStorage imageStorage;
    private final TransactionTemplate tx;

    public UserResponse upload(Long userId, MultipartFile file) {
        byte[] content = ImageFiles.read(file);
        ImageStorage.StoredImage stored = imageStorage.upload(content, "village-booker/users/" + userId);
        Replaced result;
        try {
            result = tx.execute(status -> {
                // lock: with two uploads at once each must see the other's file as the old one, or one file leaks
                User user = lock(userId);
                String old = user.getImagePublicId();
                user.setImageUrl(stored.url());
                user.setImagePublicId(stored.publicId());
                return new Replaced(UserResponse.from(user), old);
            });
        } catch (RuntimeException e) {
            imageStorage.delete(stored.publicId());
            throw e;
        }
        deleteFile(result.oldPublicId());
        return result.user();
    }

    // the user's own picture, or any user's for the admin
    public UserResponse delete(Long userId) {
        Replaced result = tx.execute(status -> {
            User user = lock(userId);
            String old = user.getImagePublicId();
            user.setImageUrl(null);
            user.setImagePublicId(null);
            return new Replaced(UserResponse.from(user), old);
        });
        deleteFile(result.oldPublicId());
        return result.user();
    }

    private User lock(Long userId) {
        return userRepo.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "User " + userId + " not found."));
    }

    private void deleteFile(String publicId) {
        if (publicId != null) {
            imageStorage.delete(publicId);
        }
    }

    private record Replaced(UserResponse user, String oldPublicId) {
    }
}
