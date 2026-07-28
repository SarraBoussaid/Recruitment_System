package com.recruitment.dto;

public record CompanyJobSummaryDto(
        int id,
        String title,
        String location,
        String type,
        int applicantCount,
        String postedAt
) {}
