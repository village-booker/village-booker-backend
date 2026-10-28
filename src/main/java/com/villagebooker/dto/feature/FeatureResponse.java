package com.villagebooker.dto.feature;

import com.villagebooker.model.Feature;

public record FeatureResponse(Long id, String nameSr, String nameEn) {

    public static FeatureResponse from(Feature feature) {
        return new FeatureResponse(feature.getId(), feature.getNameSr(), feature.getNameEn());
    }
}
