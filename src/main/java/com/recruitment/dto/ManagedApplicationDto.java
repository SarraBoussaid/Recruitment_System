package com.recruitment.dto;

public record ManagedApplicationDto(
        int id,
        String candidateName,
        String candidateEmail,
        String candidatePhone,
        String resumeUrl,
        String jobTitle,
        String company,
        String status,
        String appliedAt
) {}
