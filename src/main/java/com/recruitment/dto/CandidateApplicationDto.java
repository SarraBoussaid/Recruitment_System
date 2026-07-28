package com.recruitment.dto;

public record CandidateApplicationDto(
        int id,
        String jobTitle,
        String company,
        String status,
        String appliedAt
) {}
