package com.villagebooker.dto.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// a host who cancels has to say why: the guest sees it and the admin uses it for penalties (spec 9.4)
public record OwnerCancellationRequest(@NotBlank @Size(max = 255) String reason) {
}
