package com.recruitment.service;

import com.recruitment.dto.JobCreateRequest;
import com.recruitment.dto.JobDto;
import com.recruitment.exception.ApiException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

@Service
public class JobService {

    private static final String STATUS_OPEN = "open";
    private static final String STATUS_CLOSED = "closed";
    private static final String STATUS_DELETED = "deleted";

    private final JdbcTemplate jdbc;

    public JobService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Search for jobs. Only returns jobs that are currently open.
     */
    public List<JobDto> findJobs(String keyword, String location, String type) {
        StringBuilder sql = new StringBuilder(
                "SELECT j.id, j.title, c.name AS company, c.industry, j.description, " +
                        "j.location, j.type, j.salary " +
                        "FROM jobs j JOIN companies c ON j.company_id = c.id " +
                        "WHERE j.status = ?"
        );

        List<Object> params = new ArrayList<>();
        params.add(STATUS_OPEN);

        if (type != null && !type.isBlank() && !"all".equalsIgnoreCase(type)) {
            sql.append(" AND j.type = ?");
            params.add(type.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (j.title LIKE ? OR c.name LIKE ? OR j.description LIKE ?)");
            String pattern = "%" + keyword.trim() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        if (location != null && !location.isBlank()) {
            sql.append(" AND j.location LIKE ?");
            params.add("%" + location.trim() + "%");
        }

        sql.append(" ORDER BY j.posted_at DESC");

        return jdbc.query(sql.toString(), (rs, rowNum) -> new JobDto(
                rs.getInt("id"),
                rs.getString("title"),
                rs.getString("company"),
                rs.getString("industry"),
                rs.getString("description"),
                rs.getString("location"),
                rs.getString("type"),
                rs.getString("salary")
        ), params.toArray());
    }

    /**
     * Returns the job DTO if the job is open; otherwise null.
     */
    public JobDto findById(int id) {
        String sql = "SELECT j.id, j.title, c.name AS company, c.industry, j.description, " +
                "j.location, j.type, j.salary FROM jobs j JOIN companies c ON j.company_id = c.id " +
                "WHERE j.id = ? AND j.status = ?";

        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return null;
            }
            return new JobDto(
                    rs.getInt("id"),
                    rs.getString("title"),
                    rs.getString("company"),
                    rs.getString("industry"),
                    rs.getString("description"),
                    rs.getString("location"),
                    rs.getString("type"),
                    rs.getString("salary")
            );
        }, id, STATUS_OPEN);
    }

    /**
     * Create a new job posting for the given company.
     */
    public JobDto createJob(JobCreateRequest request, int companyId) {
        String sql = "INSERT INTO jobs (company_id, title, description, location, type, salary, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, companyId);
            ps.setString(2, request.title().trim());
            ps.setString(3, request.description().trim());
            ps.setString(4, request.location().trim());
            ps.setString(5, request.type().trim());
            ps.setString(6, request.salary() != null ? request.salary().trim() : null);
            ps.setString(7, STATUS_OPEN);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ApiException(500, "Could not create job.");
        }
        return findById(key.intValue());
    }

    public void deleteJob(int jobId, int companyId) {
        Integer owner = getOwnerCompanyId(jobId);
        if (owner == null) {
            throw new ApiException(404, "Job not found");
        }
        if (!owner.equals(companyId)) {
            throw new ApiException(403, "Not authorized to delete this job");
        }

        int updated = jdbc.update("UPDATE jobs SET status = ? WHERE id = ?", STATUS_DELETED, jobId);
        if (updated == 0) {
            throw new ApiException(500, "Failed to delete job");
        }
    }

    /**
     * Mark a job as unavailable (closed). Only the owning company can perform this.
     */
    public void markUnavailable(int jobId, int companyId) {
        Integer owner = getOwnerCompanyId(jobId);
        if (owner == null) {
            throw new ApiException(404, "Job not found");
        }
        if (!owner.equals(companyId)) {
            throw new ApiException(403, "Not authorized to update this job");
        }

        int updated = jdbc.update("UPDATE jobs SET status = ? WHERE id = ?", STATUS_CLOSED, jobId);
        if (updated == 0) {
            throw new ApiException(500, "Failed to update job status");
        }
    }

    private Integer getOwnerCompanyId(int jobId) {
        try {
            return jdbc.queryForObject("SELECT company_id FROM jobs WHERE id = ?", Integer.class, jobId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }
}
