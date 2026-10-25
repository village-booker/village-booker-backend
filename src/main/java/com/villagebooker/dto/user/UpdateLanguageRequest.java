package com.villagebooker.dto.user;

import com.villagebooker.enums.Language;
import jakarta.validation.constraints.NotNull;

public record UpdateLanguageRequest(@NotNull Language language) {
}
