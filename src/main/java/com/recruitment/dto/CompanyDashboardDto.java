package com.recruitment.dto;

import java.util.List;

public record CompanyDashboardDto(
        List<SuggestedCandidateDto> suggestedCandidates,
        int openJobs,
        int unreadNotifications,
        int unreadMessages,
        int totalApplicants,
        int pendingApplicants
) {}
