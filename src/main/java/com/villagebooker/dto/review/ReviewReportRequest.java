package com.villagebooker.dto.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewReportRequest(@NotBlank @Size(max = 1000) String reason) {
}
