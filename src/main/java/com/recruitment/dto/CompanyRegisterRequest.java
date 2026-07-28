package com.recruitment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyRegisterRequest(
        @NotBlank String companyName,
        String industry,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, message = "must be at least 6 characters") String password
) {}
