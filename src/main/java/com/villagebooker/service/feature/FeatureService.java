package com.villagebooker.service.feature;

import com.villagebooker.service.listing.OwnerListingService;

import com.villagebooker.common.exception.BadRequestException;
import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.common.exception.NotFoundException;
import com.villagebooker.dto.feature.FeatureRequest;
import com.villagebooker.dto.feature.FeatureResponse;
import com.villagebooker.dto.listing.ListingFeaturesRequest;
import com.villagebooker.dto.listing.ListingResponse;
import com.villagebooker.model.Feature;
import com.villagebooker.model.Listing;
import com.villagebooker.repo.feature.FeatureRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FeatureService {

    private final FeatureRepo featureRepo;
    private final OwnerListingService ownerListingService;

    @Transactional(readOnly = true)
    public List<FeatureResponse> findAll() {
        return featureRepo.findAllByOrderByNameSr().stream().map(FeatureResponse::from).toList();
    }

    @Transactional
    public FeatureResponse create(FeatureRequest request) {
        String nameSr = request.nameSr().trim();
        String nameEn = request.nameEn().trim();
        if (featureRepo.existsByNameSrIgnoreCaseOrNameEnIgnoreCase(nameSr, nameEn)) {
            throw nameTaken();
        }
        Feature feature = new Feature();
        feature.setNameSr(nameSr);
        feature.setNameEn(nameEn);
        return FeatureResponse.from(featureRepo.save(feature));
    }

    @Transactional
    public FeatureResponse update(Long featureId, FeatureRequest request) {
        Feature feature = find(featureId);
        String nameSr = request.nameSr().trim();
        String nameEn = request.nameEn().trim();
        if (featureRepo.existsOtherWithName(featureId, nameSr, nameEn)) {
            throw nameTaken();
        }
        feature.setNameSr(nameSr);
        feature.setNameEn(nameEn);
        return FeatureResponse.from(feature);
    }

    @Transactional
    public void delete(Long featureId) {
        Feature feature = find(featureId);
        if (featureRepo.isInUse(featureId)) {
            throw new ConflictException(ErrorCode.FEATURE_IN_USE,
                    "Feature " + featureId + " is used by listings and cannot be deleted.");
        }
        featureRepo.delete(feature);
    }

    @Transactional
    public ListingResponse setListingFeatures(Long ownerId, Long listingId, ListingFeaturesRequest request) {
        Listing listing = ownerListingService.findOwn(ownerId, listingId);
        Set<Long> ids = new LinkedHashSet<>(request.featureIds());
        List<Feature> features = featureRepo.findByIdIn(ids);
        if (features.size() != ids.size()) {
            Set<Long> missing = new HashSet<>(ids);
            features.forEach(f -> missing.remove(f.getId()));
            throw new BadRequestException(ErrorCode.FEATURE_NOT_FOUND, "Unknown feature ids: " + missing,
                    Map.of("featureIds", List.copyOf(missing)));
        }
        listing.getFeatures().clear();
        listing.getFeatures().addAll(features);
        return ListingResponse.from(listing);
    }

    private Feature find(Long featureId) {
        return featureRepo.findById(featureId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.FEATURE_NOT_FOUND,
                        "Feature " + featureId + " not found."));
    }

    private static ConflictException nameTaken() {
        return new ConflictException(ErrorCode.FEATURE_NAME_TAKEN, "A feature with this name already exists.");
    }
}
