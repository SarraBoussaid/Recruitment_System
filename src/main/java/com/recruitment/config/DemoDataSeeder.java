package com.recruitment.config;

import com.recruitment.auth.UserRole;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;

@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final String COMPANY_EMAIL = "demo.company@local";
    private static final String CANDIDATE_EMAIL = "demo.candidate@local";
    private static final String DEMO_PASSWORD = "demo123";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedDemoData();
    }

    @Transactional
    public void seedDemoData() {
        Integer userCount = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        if (userCount != null && userCount > 0) {
            return;
        }

        ensureDemoData();
    }

    private void ensureDemoData() {
        int companyUserId = ensureUser(COMPANY_EMAIL, DEMO_PASSWORD, UserRole.COMPANY.name());
        int candidateUserId = ensureUser(CANDIDATE_EMAIL, DEMO_PASSWORD, UserRole.CANDIDATE.name());

        int companyId = ensureCompanyProfile(companyUserId, "Carthage Data Systems", "Software Engineering & Cloud");
        int candidateId = ensureCandidateProfile(candidateUserId, "Sarra", "Ben Salem", CANDIDATE_EMAIL, "+21620123456", "");

        int job1 = ensureJob(companyId, "Senior Full-Stack Java Engineer",
                "Leading backend architectural design using Spring Boot 3, REST APIs, and microservices. Collaborating with cross-functional teams in Tunis.",
                "Tunis", "full-time", "2,800 - 3,800 TND / mo");

        ensureJob(companyId, "DevOps & Infrastructure Specialist",
                "Managing Kubernetes clusters, CI/CD pipelines, and automated cloud deployments.",
                "Remote", "remote", "3,000 - 4,500 TND / mo");

        ensureApplication(job1, candidateId);
        ensureApplicationMessages(job1, candidateId, companyUserId, candidateUserId);
        ensureConversation(companyId, candidateId, companyUserId);
    }

    private int ensureUser(String email, String password, String role) {
        Integer existing = findFirstInt(
                "SELECT id FROM users WHERE LOWER(email) = LOWER(?)",
                email
        );
        if (existing != null) {
            return existing;
        }

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

        Number key = keyHolder.getKey();
        return key != null ? key.intValue() : -1;
    }

    private int ensureCompanyProfile(int userId, String name, String industry) {
        Integer existing = findFirstInt(
                "SELECT id FROM companies WHERE user_id = ?",
                userId
        );
        if (existing != null) {
            return existing;
        }

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

        Number key = keyHolder.getKey();
        return key != null ? key.intValue() : -1;
    }

    private int ensureCandidateProfile(int userId, String firstName, String lastName, String email, String phone, String resumeUrl) {
        Integer existing = findFirstInt(
                "SELECT id FROM candidates WHERE user_id = ?",
                userId
        );
        if (existing != null) {
            return existing;
        }

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

        Number key = keyHolder.getKey();
        return key != null ? key.intValue() : -1;
    }

    private int ensureJob(int companyId, String title, String description, String location, String type, String salary) {
        Integer existing = findFirstInt(
                "SELECT id FROM jobs WHERE company_id = ? AND title = ?",
                companyId,
                title
        );
        if (existing != null) {
            return existing;
        }

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

        Number key = keyHolder.getKey();
        return key != null ? key.intValue() : -1;
    }

    private Integer findFirstInt(String sql, Object... args) {
        return jdbc.query(
                sql,
                (rs, rowNum) -> rs.getInt(1),
                args
        ).stream().findFirst().orElse(null);
    }

    private void ensureApplication(int jobId, int candidateId) {
        Integer existing = findFirstInt(
                "SELECT id FROM applications WHERE job_id = ? AND candidate_id = ?",
                jobId,
                candidateId
        );
        if (existing != null) {
            return;
        }

        jdbc.update("INSERT INTO applications (job_id, candidate_id, status) VALUES (?, ?, 'reviewed')", jobId, candidateId);
    }

    private void ensureApplicationMessages(int jobId, int candidateId, int companyUserId, int candidateUserId) {
        Integer appId = findFirstInt(
                "SELECT id FROM applications WHERE job_id = ? AND candidate_id = ?",
                jobId,
                candidateId
        );
        if (appId == null) {
            return;
        }

        Integer messageCount = findFirstInt(
                "SELECT COUNT(*) FROM messages WHERE application_id = ?",
                appId
        );
        if (messageCount != null && messageCount > 0) {
            return;
        }

        jdbc.update(
                "INSERT INTO messages (application_id, sender_user_id, sender_role, message) VALUES (?, ?, ?, ?)",
                appId,
                companyUserId,
                UserRole.COMPANY.name(),
                "Hello Sarra — We reviewed your application for Senior Full-Stack Java Engineer and would like to schedule an initial technical discussion."
        );
        jdbc.update(
                "INSERT INTO messages (application_id, sender_user_id, sender_role, message) VALUES (?, ?, ?, ?)",
                appId,
                candidateUserId,
                UserRole.CANDIDATE.name(),
                "Hello! Thank you for the update. I would be glad to discuss the position details."
        );
    }

    private void ensureConversation(int companyId, int candidateId, int companyUserId) {
        Integer existing = findFirstInt(
                "SELECT id FROM conversations WHERE company_id = ? AND candidate_id = ?",
                companyId,
                candidateId
        );
        if (existing != null) {
            return;
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO conversations (company_id, candidate_id) VALUES (?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS
            );
            ps.setInt(1, companyId);
            ps.setInt(2, candidateId);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            return;
        }

        Integer conversationId = key.intValue();
        Integer messageCount = findFirstInt(
                "SELECT COUNT(*) FROM messages WHERE conversation_id = ?",
                conversationId
        );
        if (messageCount != null && messageCount > 0) {
            return;
        }

        jdbc.update(
                "INSERT INTO messages (conversation_id, sender_user_id, sender_role, message) VALUES (?, ?, ?, ?)",
                conversationId,
                companyUserId,
                UserRole.COMPANY.name(),
                "We can also coordinate schedule availability directly here."
        );
    }
}
