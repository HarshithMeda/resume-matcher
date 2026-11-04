package com.harshith.resumematcher.analysis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AnalysisRequest(
        @NotNull @Positive Long resumeId,
        @NotBlank
        @Size(min = 50, max = 15_000, message = "must be between 50 and 15,000 characters")
        String jobDescription) {
}
