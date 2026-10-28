package com.villagebooker.service.listing;

import com.villagebooker.common.exception.BadRequestException;
import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.common.exception.NotFoundException;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.listing.AdminImageResponse;
import com.villagebooker.dto.listing.ImageOrderRequest;
import com.villagebooker.dto.listing.ListingImageResponse;
import com.villagebooker.dto.listing.RejectionRequest;
import com.villagebooker.enums.listing.ImageStatus;
import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.model.Listing;
import com.villagebooker.model.ListingImage;
import com.villagebooker.repo.listing.ListingImageRepo;
import com.villagebooker.repo.listing.ListingRepo;
import com.villagebooker.storage.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Images of a listing. The owner adds, orders and deletes them directly (no change request), but guests see only
// APPROVED images: an image added to a public listing (APPROVED, PAUSED_BY_OWNER) is PENDING until the admin approves
// it; images of a listing that is not public yet are reviewed together with the listing, so they are APPROVED at once.
// A rejected image is removed from the storage, its row stays with the reason for the owner.
//
// The upload to the storage takes seconds, so it runs OUTSIDE a database transaction (CLAUDE.md): short transaction
// to check, upload, short transaction to save. If saving fails, the uploaded file is deleted again; a file is deleted
// from the storage only after its row is gone / rejected.
@Service
@RequiredArgsConstructor
public class ListingImageService {

    static final int MAX_IMAGES = 15;
    static final int MAX_BYTES = 5 * 1024 * 1024;

    // statuses in which guests can see the listing, so new images need a review
    private static final Set<ListingStatus> PUBLIC = Set.of(ListingStatus.APPROVED, ListingStatus.PAUSED_BY_OWNER);

    private final ListingRepo listingRepo;
    private final ListingImageRepo listingImageRepo;
    private final OwnerListingService ownerListingService;
    private final ImageStorage imageStorage;
    private final TransactionTemplate tx;

    public ListingImageResponse upload(Long ownerId, Long listingId, MultipartFile file) {
        byte[] content = checkFile(file);
        tx.executeWithoutResult(status -> checkLimit(ownerListingService.findOwn(ownerId, listingId)));

        ImageStorage.StoredImage stored = imageStorage.upload(content, "village-booker/listings/" + listingId);
        try {
            return tx.execute(status -> {
                // lock: two uploads at once would get the same sortOrder or pass the limit together
                Listing listing = listingRepo.findByIdForUpdate(listingId)
                        .orElseThrow(() -> OwnerListingService.listingNotFound(listingId));
                OwnerListingService.checkOwner(listing, ownerId);
                checkLimit(listing);

                ListingImage image = new ListingImage();
                image.setListing(listing);
                image.setUrl(stored.url());
                image.setPublicId(stored.publicId());
                image.setStatus(PUBLIC.contains(listing.getStatus()) ? ImageStatus.PENDING : ImageStatus.APPROVED);
                image.setSortOrder(Integer.MAX_VALUE);
                listing.getImages().add(image);
                renumber(listing);
                listingRepo.flush();
                return ListingImageResponse.from(image);
            });
        } catch (RuntimeException e) {
            imageStorage.delete(stored.publicId());
            throw e;
        }
    }

    public void delete(Long ownerId, Long listingId, Long imageId) {
        String publicId = tx.execute(status -> removeImage(ownerListingService.findOwn(ownerId, listingId), imageId));
        imageStorage.delete(publicId);
    }

    // ids of all images that are not REJECTED, in the new order
    public List<ListingImageResponse> reorder(Long ownerId, Long listingId, ImageOrderRequest request) {
        return tx.execute(status -> {
            Listing listing = ownerListingService.findOwn(ownerId, listingId);
            List<ListingImage> active = listing.getImages().stream().filter(ListingImageService::isActive).toList();
            List<Long> ids = request.imageIds();
            boolean sameImages = ids.size() == active.size()
                    && new HashSet<>(ids).size() == ids.size()
                    && active.stream().allMatch(i -> ids.contains(i.getId()));
            if (!sameImages) {
                throw new BadRequestException(ErrorCode.IMAGE_ORDER_INVALID,
                        "imageIds must contain every image of the listing (except rejected ones) exactly once.");
            }
            active.forEach(i -> i.setSortOrder(ids.indexOf(i.getId())));
            renumber(listing);
            return sorted(listing).stream().map(ListingImageResponse::from).toList();
        });
    }

    public PageResponse<AdminImageResponse> findByStatus(ImageStatus status, Pageable pageable) {
        return tx.execute(s -> PageResponse.of(listingImageRepo.findByStatus(status, pageable), AdminImageResponse::from));
    }

    public AdminImageResponse approve(Long imageId) {
        return tx.execute(status -> {
            ListingImage image = findPending(imageId);
            image.setStatus(ImageStatus.APPROVED);
            return AdminImageResponse.from(image);
        });
    }

    public AdminImageResponse reject(Long imageId, RejectionRequest request) {
        ListingImage rejected = tx.execute(status -> {
            ListingImage image = findPending(imageId);
            image.setStatus(ImageStatus.REJECTED);
            image.setRejectionReason(request.reason().trim());
            renumber(image.getListing());
            return image;
        });
        imageStorage.delete(rejected.getPublicId());
        return tx.execute(status -> AdminImageResponse.from(listingImageRepo.findById(imageId).orElseThrow()));
    }

    public void deleteAsAdmin(Long listingId, Long imageId) {
        String publicId = tx.execute(status -> removeImage(
                listingRepo.findById(listingId).orElseThrow(() -> OwnerListingService.listingNotFound(listingId)),
                imageId));
        imageStorage.delete(publicId);
    }

    private ListingImage findPending(Long imageId) {
        ListingImage image = listingImageRepo.findById(imageId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.IMAGE_NOT_FOUND, "Image " + imageId + " not found."));
        if (image.getStatus() != ImageStatus.PENDING) {
            throw new ConflictException(ErrorCode.IMAGE_NOT_PENDING, "Image " + imageId + " is not waiting for review.",
                    Map.of("status", image.getStatus().name()));
        }
        return image;
    }

    // returns the storage id of the removed image
    private static String removeImage(Listing listing, Long imageId) {
        ListingImage image = listing.getImages().stream()
                .filter(i -> i.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(ErrorCode.IMAGE_NOT_FOUND,
                        "Image " + imageId + " not found on listing " + listing.getId() + "."));
        listing.getImages().remove(image);
        renumber(listing);
        return image.getPublicId();
    }

    // images that are not REJECTED get 0..n-1 in their current order, REJECTED ones follow
    private static void renumber(Listing listing) {
        List<ListingImage> ordered = sorted(listing);
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setSortOrder(i);
        }
    }

    private static List<ListingImage> sorted(Listing listing) {
        return listing.getImages().stream()
                .sorted(Comparator.comparing((ListingImage i) -> !isActive(i))
                        .thenComparing(ListingImage::getSortOrder))
                .toList();
    }

    private static boolean isActive(ListingImage image) {
        return image.getStatus() != ImageStatus.REJECTED;
    }

    private static void checkLimit(Listing listing) {
        if (listing.getImages().stream().filter(ListingImageService::isActive).count() >= MAX_IMAGES) {
            throw new BadRequestException(ErrorCode.IMAGE_LIMIT_REACHED,
                    "A listing can have at most " + MAX_IMAGES + " images.", Map.of("max", MAX_IMAGES));
        }
    }

    // size and real file type (first bytes), not the Content-Type the client claims
    private static byte[] checkFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalidType();
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException(ErrorCode.IMAGE_TOO_LARGE, "An image can have at most 5 MB.",
                    Map.of("maxMb", 5));
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
