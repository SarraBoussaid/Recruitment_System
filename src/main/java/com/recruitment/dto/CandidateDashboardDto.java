package com.recruitment.dto;

import java.util.List;
import java.util.Map;

public record CandidateDashboardDto(
        List<CandidateApplicationDto> applications,
        int unreadNotifications,
        int unreadMessages,
        String resumeUrl,
        String phone,
        Map<String, Integer> statusCounts
) {}
