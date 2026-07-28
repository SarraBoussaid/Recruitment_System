package com.recruitment.service;

import com.recruitment.auth.UserRole;
import com.recruitment.dto.ApplicationRequest;
import com.recruitment.dto.ApplicationResponse;
import com.recruitment.dto.CandidateApplicationDto;
import com.recruitment.dto.ContactRequest;
import com.recruitment.dto.ManagedApplicationDto;
import com.recruitment.dto.UserProfileDto;
import com.recruitment.exception.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

@Service
public class ApplicationService {

    private final JdbcTemplate jdbc;
    private final JobService jobService;
    private final NotificationService notificationService;
    private final MessageService messageService;

    public ApplicationService(
            JdbcTemplate jdbc,
            JobService jobService,
            NotificationService notificationService,
            MessageService messageService
    ) {
        this.jdbc = jdbc;
        this.jobService = jobService;
        this.notificationService = notificationService;
        this.messageService = messageService;
    }

    public ApplicationResponse submit(ApplicationRequest request, UserProfileDto candidate) {
        if (jobService.findById(request.jobId()) == null) {
            throw new ApiException(404, "Job not found or no longer open.");
        }

        updateCandidateProfile(candidate.candidateId(), request);
        int applicationId = createApplication(request.jobId(), candidate.candidateId());
        notifyNewApplication(request.jobId(), candidate, applicationId);

        return new ApplicationResponse(
                applicationId,
                "Application submitted successfully."
        );
    }

    private void updateCandidateProfile(int candidateId, ApplicationRequest request) {
        jdbc.update(
                """
                UPDATE candidates
                SET phone = COALESCE(?, phone), resume_url = COALESCE(?, resume_url)
                WHERE id = ?
                """,
                request.phone(),
                request.resumeUrl(),
                candidateId
        );
    }

    private int createApplication(int jobId, int candidateId) {
        String sql = "INSERT INTO applications (job_id, candidate_id, status) VALUES (?, ?, 'pending')";

        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
                ps.setInt(1, jobId);
                ps.setInt(2, candidateId);
                return ps;
            }, keyHolder);

            Number key = keyHolder.getKey();
            if (key == null) {
                throw new ApiException(500, "Could not save application.");
            }
            return key.intValue();
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(409, "You have already applied for this job.");
        }
    }

    public List<CandidateApplicationDto> findByCandidateId(int candidateId) {
        String sql = """
                SELECT a.id, j.title AS job_title, c.name AS company, a.status, a.applied_at
                FROM applications a
                JOIN jobs j ON a.job_id = j.id
                JOIN companies c ON j.company_id = c.id
                WHERE a.candidate_id = ?
                ORDER BY a.applied_at DESC
                """;

        return jdbc.query(sql, (rs, rowNum) -> new CandidateApplicationDto(
                rs.getInt("id"),
                rs.getString("job_title"),
                rs.getString("company"),
                rs.getString("status"),
                formatDateTime(rs.getTimestamp("applied_at"))
        ), candidateId);
    }

    public List<ManagedApplicationDto> findAllForCompany(int companyId) {
        String sql = """
                SELECT a.id,
                       CONCAT(ca.first_name, ' ', ca.last_name) AS candidate_name,
                       ca.email AS candidate_email,
                       ca.phone AS candidate_phone,
                       ca.resume_url,
                       j.title AS job_title,
                       c.name AS company,
                       a.status,
                       a.applied_at
                FROM applications a
                JOIN candidates ca ON a.candidate_id = ca.id
                JOIN jobs j ON a.job_id = j.id
                JOIN companies c ON j.company_id = c.id
                WHERE c.id = ?
                ORDER BY a.applied_at DESC
                """;

        return jdbc.query(sql, (rs, rowNum) -> new ManagedApplicationDto(
                rs.getInt("id"),
                rs.getString("candidate_name"),
                rs.getString("candidate_email"),
                rs.getString("candidate_phone"),
                rs.getString("resume_url"),
                rs.getString("job_title"),
                rs.getString("company"),
                rs.getString("status"),
                formatDateTime(rs.getTimestamp("applied_at"))
        ), companyId);
    }

    public void updateStatus(int id, String status, int companyId) {
        if (!isValidStatus(status)) {
            throw new ApiException(400, "Invalid status. Use: pending, reviewed, interview, accepted, rejected.");
        }

        int updated = jdbc.update(
                """
                UPDATE applications SET status = ?
                WHERE id = ?
                  AND job_id IN (SELECT id FROM jobs WHERE company_id = ?)
                """,
                status.trim().toLowerCase(),
                id,
                companyId
        );

        if (updated == 0) {
            throw new ApiException(404, "Application not found.");
        }

        notifyStatusChange(id, status.trim().toLowerCase());
    }

    public void contactCandidate(int applicationId, int companyId, int companyUserId, ContactRequest request) {
        messageService.sendApplicationMessage(
                applicationId,
                companyUserId,
                UserRole.COMPANY,
                null,
                companyId,
                request
        );
    }

    private void notifyNewApplication(int jobId, UserProfileDto candidate, int applicationId) {
        try {
            Integer companyUserId = notificationService.findCompanyUserIdByJobId(jobId);
            if (companyUserId == null) {
                return;
            }
            String jobTitle = jdbc.queryForObject(
                    "SELECT title FROM jobs WHERE id = ?",
                    String.class,
                    jobId
            );
            notificationService.notify(
                    companyUserId,
                    "NEW_APPLICATION",
                    "New application received",
                    candidate.displayName() + " applied for " + jobTitle,
                    applicationId
            );
        } catch (Exception ignored) {
            // Notifications should not block the main flow
        }
    }

    private void notifyStatusChange(int applicationId, String status) {
        try {
            Integer candidateUserId = notificationService.findCandidateUserIdByApplicationId(applicationId);
            if (candidateUserId == null) {
                return;
            }
            Map<String, Object> details = jdbc.queryForMap(
                    """
                    SELECT j.title AS job_title, c.name AS company_name
                    FROM applications a
                    JOIN jobs j ON a.job_id = j.id
                    JOIN companies c ON j.company_id = c.id
                    WHERE a.id = ?
                    """,
                    applicationId
            );
            String jobTitle = (String) details.get("job_title");
            String companyName = (String) details.get("company_name");
            notificationService.notify(
                    candidateUserId,
                    "STATUS_UPDATE",
                    "Application status updated",
                    companyName + " marked your application for " + jobTitle + " as " + status,
                    applicationId
            );
        } catch (Exception ignored) {
            // Notifications should not block the main flow
        }
    }

    private boolean isValidStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        return switch (status.trim().toLowerCase()) {
            case "pending", "reviewed", "interview", "accepted", "rejected" -> true;
            default -> false;
        };
    }

    private String formatDateTime(java.sql.Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }
        return timestamp.toLocalDateTime().toString().replace('T', ' ');
    }
}
