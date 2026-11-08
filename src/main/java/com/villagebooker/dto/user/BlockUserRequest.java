package com.villagebooker.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// the reason is seen only by admins
public record BlockUserRequest(@NotBlank @Size(max = 1000) String reason) {
}
