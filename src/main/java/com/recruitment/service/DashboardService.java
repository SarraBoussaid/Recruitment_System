package com.recruitment.service;

import com.recruitment.dto.CandidateApplicationDto;
import com.recruitment.dto.CandidateDashboardDto;
import com.recruitment.dto.CompanyDashboardDto;
import com.recruitment.dto.SuggestedCandidateDto;
import com.recruitment.dto.ManagedApplicationDto;
import com.recruitment.dto.UserProfileDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DashboardService {

    private final JdbcTemplate jdbc;
    private final ApplicationService applicationService;
    private final NotificationService notificationService;
    private final MessageService messageService;

    public DashboardService(
            JdbcTemplate jdbc,
            ApplicationService applicationService,
            NotificationService notificationService,
            MessageService messageService
    ) {
        this.jdbc = jdbc;
        this.applicationService = applicationService;
        this.notificationService = notificationService;
        this.messageService = messageService;
    }

    public CandidateDashboardDto getCandidateDashboard(UserProfileDto candidate) {
        List<CandidateApplicationDto> applications =
                applicationService.findByCandidateId(candidate.candidateId());

        Map<String, Integer> statusCounts = new HashMap<>();
        for (String status : List.of("pending", "reviewed", "interview", "accepted", "rejected")) {
            statusCounts.put(status, 0);
        }
        for (CandidateApplicationDto app : applications) {
            statusCounts.merge(app.status(), 1, Integer::sum);
        }

        return new CandidateDashboardDto(
                applications,
                notificationService.unreadCount(candidate.userId()),
                messageService.messageCountForCandidate(candidate.candidateId()),
                candidate.resumeUrl(),
                candidate.phone(),
                statusCounts
        );
    }

    public CompanyDashboardDto getCompanyDashboard(UserProfileDto company) {
        List<SuggestedCandidateDto> suggestedCandidates =
                findSuggestedCandidates(company.companyId());

        Integer openJobs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM jobs WHERE company_id = ? AND status = 'open'",
                Integer.class,
                company.companyId()
        );

        Integer totalApplicants = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM applications a
                JOIN jobs j ON a.job_id = j.id
                WHERE j.company_id = ?
                """,
                Integer.class,
                company.companyId()
        );

        Integer pendingApplicants = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM applications a
                JOIN jobs j ON a.job_id = j.id
                WHERE j.company_id = ? AND a.status = 'pending'
                """,
                Integer.class,
                company.companyId()
        );

        return new CompanyDashboardDto(
                suggestedCandidates,
                openJobs != null ? openJobs : 0,
                notificationService.unreadCount(company.userId()),
                messageService.messageCountForCompany(company.companyId()),
                totalApplicants != null ? totalApplicants : 0,
                pendingApplicants != null ? pendingApplicants : 0
        );
    }

    private List<SuggestedCandidateDto> findSuggestedCandidates(int companyId) {
        List<SuggestedCandidateDto> suggestions = new ArrayList<>();
        Set<Integer> seenCandidateIds = new HashSet<>();

        jdbc.query(
                """
                SELECT a.id AS application_id,
                       ca.id AS candidate_id,
                       CONCAT(ca.first_name, ' ', ca.last_name) AS name,
                       ca.email,
                       ca.phone,
                       ca.resume_url,
                       j.title AS matched_job,
                       a.status
                FROM applications a
                JOIN candidates ca ON a.candidate_id = ca.id
                JOIN jobs j ON a.job_id = j.id
                WHERE j.company_id = ?
                  AND a.status IN ('interview', 'reviewed', 'pending')
                ORDER BY
                    CASE a.status
                        WHEN 'interview' THEN 0
                        WHEN 'reviewed' THEN 1
                        ELSE 2
                    END,
                    a.applied_at DESC
                """,
                rs -> {
                    while (rs.next() && suggestions.size() < 8) {
                        int candidateId = rs.getInt("candidate_id");
                        if (!seenCandidateIds.add(candidateId)) {
                            continue;
                        }
                        String jobTitle = rs.getString("matched_job");
                        suggestions.add(new SuggestedCandidateDto(
                                candidateId,
                                rs.getInt("application_id"),
                                rs.getString("name"),
                                rs.getString("email"),
                                rs.getString("phone"),
                                rs.getString("resume_url"),
                                jobTitle,
                                rs.getString("status"),
                                "Applied to your company · " + jobTitle
                        ));
                    }
                    return null;
                },
                companyId
        );

        if (suggestions.size() < 8) {
            jdbc.query(
                    """
                    SELECT ca.id AS candidate_id,
                           CONCAT(ca.first_name, ' ', ca.last_name) AS name,
                           ca.email,
                           ca.phone,
                           ca.resume_url
                    FROM candidates ca
                    WHERE NOT EXISTS (
                          SELECT 1 FROM applications a
                          JOIN jobs j ON a.job_id = j.id
                          WHERE a.candidate_id = ca.id AND j.company_id = ?
                      )
                    ORDER BY CASE WHEN ca.resume_url IS NOT NULL THEN 0 ELSE 1 END, ca.created_at DESC
                    """,
                    rs -> {
                        while (rs.next() && suggestions.size() < 8) {
                            int candidateId = rs.getInt("candidate_id");
                            if (!seenCandidateIds.add(candidateId)) {
                                continue;
                            }
                            suggestions.add(new SuggestedCandidateDto(
                                    candidateId,
                                    null,
                                    rs.getString("name"),
                                    rs.getString("email"),
                                    rs.getString("phone"),
                                    rs.getString("resume_url"),
                                    null,
                                    null,
                                    "On the platform — available to hire"
                            ));
                        }
                        return null;
                    },
                    companyId
            );
        }

        return suggestions;
    }

}
