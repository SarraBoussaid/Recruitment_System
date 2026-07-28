package com.recruitment.dto;

import jakarta.validation.constraints.NotBlank;

public record ContactRequest(
        @NotBlank String message
) {}
