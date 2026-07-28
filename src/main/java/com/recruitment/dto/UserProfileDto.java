package com.recruitment.dto;

import com.recruitment.auth.UserRole;

public record UserProfileDto(
        int userId,
        String email,
        UserRole role,
        String displayName,
        Integer companyId,
        String companyName,
        Integer candidateId,
        String firstName,
        String lastName,
        String phone,
        String resumeUrl
) {}
