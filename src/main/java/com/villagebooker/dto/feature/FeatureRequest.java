package com.villagebooker.dto.feature;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FeatureRequest(
        @NotBlank @Size(max = 100) String nameSr,
        @NotBlank @Size(max = 100) String nameEn
) {
}
