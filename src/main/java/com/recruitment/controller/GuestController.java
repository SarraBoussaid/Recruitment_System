package com.recruitment.controller;

import com.recruitment.auth.SessionKeys;
import com.recruitment.auth.UserRole;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple dev-only guest/demo endpoint to create a sample company, candidate, jobs and a message.
 * Intended for local testing only. Calling POST /api/guest/demo will create demo data (if missing)
 * and store the session as the requested role (company or candidate).
 */
@RestController
@RequestMapping("/api/guest")
public class GuestController {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public GuestController(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/demo")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> createDemo(@RequestParam(defaultValue = "company") String role, HttpSession session) {
        // Safe fixed credentials for demo accounts
        final String companyEmail = "demo.company@local";
        final String candidateEmail = "demo.candidate@local";
        final String demoPassword = "demo123";

        int companyUserId = ensureUser(companyEmail, demoPassword, UserRole.COMPANY.name());
        int candidateUserId = ensureUser(candidateEmail, demoPassword, UserRole.CANDIDATE.name());

        int companyId = ensureCompanyProfile(companyUserId, "DemoTech Ltd", "Software");
        int candidateId = ensureCandidateProfile(candidateUserId, "Sarra", "Ben", candidateEmail, "+21612345678", "");

        int jobId = ensureJob(companyId, "Full Stack Developer", "Build polished product experiences and ship features end to end.", "Tunis", "full-time", "Negotiable");

        ensureApplication(jobId, candidateId);

        ensureMessageForApplication(jobId, candidateId, companyUserId, candidateUserId);

        // set session as requested role
        if ("company".equalsIgnoreCase(role)) {
            session.setAttribute(SessionKeys.USER_ID, companyUserId);
            session.setAttribute(SessionKeys.ROLE, UserRole.COMPANY.name());
            session.setAttribute(SessionKeys.COMPANY_ID, companyId);
            session.removeAttribute(SessionKeys.CANDIDATE_ID);
        } else {
            session.setAttribute(SessionKeys.USER_ID, candidateUserId);
            session.setAttribute(SessionKeys.ROLE, UserRole.CANDIDATE.name());
            session.setAttribute(SessionKeys.CANDIDATE_ID, candidateId);
            session.removeAttribute(SessionKeys.COMPANY_ID);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("companyEmail", companyEmail);
        result.put("candidateEmail", candidateEmail);
        result.put("password", demoPassword);
        result.put("loggedInAs", role.equalsIgnoreCase("company") ? "company" : "candidate");
        result.put("jobId", jobId);
        return result;
    }

    private int ensureUser(String email, String password, String role) {
        Integer existing = findFirstInt(
                "SELECT id FROM users WHERE LOWER(email) = LOWER(?)",
                email
        );
        if (existing != null) return existing;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO users (email, password_hash, role) VALUES (?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, email);
            ps.setString(2, passwordEncoder.encode(password));
            ps.setString(3, role);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    private int ensureCompanyProfile(int userId, String name, String industry) {
        Integer existing = findFirstInt(
                "SELECT id FROM companies WHERE user_id = ?",
                userId
        );
        if (existing != null) return existing;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO companies (user_id, name, industry) VALUES (?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS
            );
            ps.setInt(1, userId);
            ps.setString(2, name);
            ps.setString(3, industry);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    private int ensureCandidateProfile(int userId, String firstName, String lastName, String email, String phone, String resumeUrl) {
        Integer existing = findFirstInt(
                "SELECT id FROM candidates WHERE user_id = ?",
                userId
        );
        if (existing != null) return existing;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO candidates (user_id, first_name, last_name, email, phone, resume_url) VALUES (?, ?, ?, ?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS
            );
            ps.setInt(1, userId);
            ps.setString(2, firstName);
            ps.setString(3, lastName);
            ps.setString(4, email);
            ps.setString(5, phone);
            ps.setString(6, resumeUrl);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    private int ensureJob(int companyId, String title, String description, String location, String type, String salary) {
        Integer existing = findFirstInt(
                "SELECT id FROM jobs WHERE company_id = ? AND title = ?",
                companyId,
                title
        );
        if (existing != null) return existing;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO jobs (company_id, title, description, location, type, salary, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS
            );
            ps.setInt(1, companyId);
            ps.setString(2, title);
            ps.setString(3, description);
            ps.setString(4, location);
            ps.setString(5, type);
            ps.setString(6, salary);
            ps.setString(7, "open");
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    private void ensureApplication(int jobId, int candidateId) {
        Integer existing = findFirstInt(
                "SELECT id FROM applications WHERE job_id = ? AND candidate_id = ?",
                jobId,
                candidateId
        );
        if (existing != null) return;
        jdbc.update("INSERT INTO applications (job_id, candidate_id, status) VALUES (?, ?, 'pending')", jobId, candidateId);
    }

    private void ensureMessageForApplication(int jobId, int candidateId, int companyUserId, int candidateUserId) {
        Integer appId = findFirstInt(
                "SELECT id FROM applications WHERE job_id = ? AND candidate_id = ?",
                jobId,
                candidateId
        );
        if (appId == null) return;

        Integer existing = findFirstInt(
                "SELECT COUNT(*) FROM messages WHERE application_id = ?",
                appId
        );
        if (existing != null && existing > 0) return;

        jdbc.update("INSERT INTO messages (application_id, sender_user_id, sender_role, message) VALUES (?, ?, ?, ?)",
                appId, companyUserId, UserRole.COMPANY.name(), "Hello — we saw your profile and would like to talk.");
        jdbc.update("INSERT INTO messages (application_id, sender_user_id, sender_role, message) VALUES (?, ?, ?, ?)",
                appId, candidateUserId, UserRole.CANDIDATE.name(), "Thanks — I'd love to hear more.");
    }

    private Integer findFirstInt(String sql, Object... args) {
        return jdbc.query(
                sql,
                (rs, rowNum) -> rs.getInt(1),
                args
        ).stream().findFirst().orElse(null);
    }
}
