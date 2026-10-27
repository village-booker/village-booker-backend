package com.villagebooker.dto.listing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// admin rejects a listing or a change request; the reason is shown to the owner, so it should say what to fix
public record RejectionRequest(@NotBlank @Size(max = 1000) String reason) {
}
