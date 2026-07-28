package com.recruitment.dto;

import jakarta.validation.constraints.NotBlank;

public record JobCreateRequest(
        @NotBlank String title,
        @NotBlank String description,
        @NotBlank String location,
        @NotBlank String type,
        String salary
) {}
