package com.villagebooker.storage;

import com.villagebooker.common.exception.BadRequestException;
import com.villagebooker.common.exception.ErrorCode;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

// Checks an uploaded image before it goes to the storage (listing images, profile pictures):
// size and the real file type from its first bytes, not the Content-Type the client claims.
public final class ImageFiles {

    public static final int MAX_MB = 5;
    public static final int MAX_BYTES = MAX_MB * 1024 * 1024;

    private ImageFiles() {
    }

    public static byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalidType();
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException(ErrorCode.IMAGE_TOO_LARGE, "An image can have at most " + MAX_MB + " MB.",
                    Map.of("maxMb", MAX_MB));
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!isJpeg(content) && !isPng(content) && !isWebp(content)) {
            throw invalidType();
        }
        return content;
    }

    private static BadRequestException invalidType() {
        return new BadRequestException(ErrorCode.IMAGE_INVALID_TYPE, "Only JPEG, PNG and WebP images are allowed.");
    }

    private static boolean isJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    private static boolean isWebp(byte[] b) {
        return b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }
}
