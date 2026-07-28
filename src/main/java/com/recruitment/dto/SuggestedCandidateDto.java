package com.recruitment.dto;

public record SuggestedCandidateDto(
        int candidateId,
        Integer applicationId,
        String name,
        String email,
        String phone,
        String resumeUrl,
        String matchedJobTitle,
        String status,
        String suggestionReason
) {}
