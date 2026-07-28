package com.recruitment.service;

import java.sql.PreparedStatement;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.recruitment.auth.SessionKeys;
import com.recruitment.auth.UserRole;
import com.recruitment.dto.CandidateRegisterRequest;
import com.recruitment.dto.CompanyRegisterRequest;
import com.recruitment.dto.LoginRequest;
import com.recruitment.dto.UserProfileDto;
import com.recruitment.exception.ApiException;

import jakarta.servlet.http.HttpSession;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password.";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private String passwordColumnName;

    private static record UserRow(int id, String email, String storedPassword, String role) {}

    public AuthService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserProfileDto registerCandidate(CandidateRegisterRequest request, HttpSession session) {
        ensureEmailAvailable(request.email());

        int userId = createUser(request.email(), request.password(), UserRole.CANDIDATE);
        int candidateId = createCandidate(userId, request);

        storeSession(session, userId, UserRole.CANDIDATE, candidateId);

        return buildProfile(userId);
    }

    @Transactional
    public UserProfileDto registerCompany(CompanyRegisterRequest request, HttpSession session) {
        ensureEmailAvailable(request.email());

        int userId = createUser(request.email(), request.password(), UserRole.COMPANY);
        int companyId = createCompany(userId, request);

        storeSession(session, userId, UserRole.COMPANY, companyId);

        return buildProfile(userId);
    }

    public UserProfileDto login(LoginRequest request, HttpSession session) {
        String passwordColumn = getPasswordColumn();
        UserRow user = jdbc.query(
                "SELECT id, email, " + passwordColumn + ", role FROM users WHERE LOWER(email) = LOWER(?)",
                ps -> {
                    ps.setString(1, request.email().trim());
                },
                rs -> rs.next() ? new UserRow(
                        rs.getInt(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4)
                ) : null
        );

        if (user == null) {
            throw new ApiException(401, INVALID_CREDENTIALS);
        }

        String storedPassword = user.storedPassword();
        if (storedPassword == null || storedPassword.isBlank()) {
            throw new ApiException(401, INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(request.password(), storedPassword)) {
            if (isProbablyPlainPassword(storedPassword) && storedPassword.equals(request.password())) {
                updateLegacyPassword(user.id(), request.password());
            } else {
                throw new ApiException(401, INVALID_CREDENTIALS);
            }
        }

        UserRole role = UserRole.valueOf(user.role());
        storeSession(session, user.id(), role, findProfileId(role, user.id()));

        return buildProfile(user.id());
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }

    public UserProfileDto getCurrentUser(HttpSession session) {
        Integer userId = (Integer) session.getAttribute(SessionKeys.USER_ID);
        if (userId == null) {
            throw new ApiException(401, "Not logged in.");
        }
        return buildProfile(userId);
    }

    public UserProfileDto getCurrentUserById(int userId) {
        return buildProfile(userId);
    }

    public UserProfileDto requireCompany(HttpSession session) {
        UserProfileDto profile = getCurrentUser(session);
        if (profile.role() != UserRole.COMPANY || profile.companyId() == null) {
            throw new ApiException(403, "Company account required.");
        }
        return profile;
    }

    public UserProfileDto requireCandidate(HttpSession session) {
        UserProfileDto profile = getCurrentUser(session);
        if (profile.role() != UserRole.CANDIDATE || profile.candidateId() == null) {
            throw new ApiException(403, "Candidate account required.");
        }
        return profile;
    }

    private void storeSession(HttpSession session, int userId, UserRole role, int profileId) {
        session.setAttribute(SessionKeys.USER_ID, userId);
        session.setAttribute(SessionKeys.ROLE, role.name());

        if (role == UserRole.COMPANY) {
            session.setAttribute(SessionKeys.COMPANY_ID, profileId);
            session.removeAttribute(SessionKeys.CANDIDATE_ID);
            return;
        }

        session.setAttribute(SessionKeys.CANDIDATE_ID, profileId);
        session.removeAttribute(SessionKeys.COMPANY_ID);
    }

    private int findProfileId(UserRole role, int userId) {
        String sql = role == UserRole.COMPANY
                ? "SELECT id FROM companies WHERE user_id = ?"
                : "SELECT id FROM candidates WHERE user_id = ?";
        try {
            return jdbc.queryForObject(
                    sql,
                    Integer.class,
                    userId
            );
        } catch (EmptyResultDataAccessException ex) {
            throw new ApiException(500, "Account profile is missing.");
        }
    }

    private void ensureEmailAvailable(String email) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?)",
                Integer.class,
                email.trim()
        );
        if (count != null && count > 0) {
            throw new ApiException(409, "An account with this email already exists.");
        }
    }

    private int createUser(String email, String password, UserRole role) {
        String passwordColumn = getPasswordColumn();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO users (email, " + passwordColumn + ", role) VALUES (?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, email.trim());
            ps.setString(2, passwordEncoder.encode(password));
            ps.setString(3, role.name());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ApiException(500, "Could not create account.");
        }
        return key.intValue();
    }

    private int createCandidate(int userId, CandidateRegisterRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO candidates (user_id, first_name, last_name, email, phone, resume_url)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    java.sql.Statement.RETURN_GENERATED_KEYS
            );
            ps.setInt(1, userId);
            ps.setString(2, request.firstName().trim());
            ps.setString(3, request.lastName().trim());
            ps.setString(4, request.email().trim());
            ps.setString(5, request.phone());
            ps.setString(6, request.resumeUrl());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ApiException(500, "Could not create candidate profile.");
        }
        return key.intValue();
    }

    private String getPasswordColumn() {
        if (passwordColumnName != null) {
            return passwordColumnName;
        }
        synchronized (this) {
            if (passwordColumnName != null) {
                return passwordColumnName;
            }
            passwordColumnName = detectPasswordColumn();
            return passwordColumnName;
        }
    }

    private boolean isProbablyPlainPassword(String hash) {
        return hash != null && !hash.startsWith("$2a$") && !hash.startsWith("$2b$") && !hash.startsWith("$2y$");
    }

    private void updateLegacyPassword(int userId, String password) {
        String hashed = passwordEncoder.encode(password);
        if (columnExists("password_hash")) {
            jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", hashed, userId);
        } else if (columnExists("password")) {
            jdbc.update("UPDATE users SET password = ? WHERE id = ?", hashed, userId);
        } else {
            throw new ApiException(500, "Users table must contain either password_hash or password column.");
        }
    }

    private String detectPasswordColumn() {
        if (columnExists("password_hash")) {
            return "password_hash";
        }
        if (columnExists("password")) {
            return "password";
        }
        throw new ApiException(500, "Users table must contain either password_hash or password column.");
    }

    private boolean columnExists(String column) {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE upper(table_name) = 'USERS' AND upper(column_name) = ? AND table_schema = database()",
                    Integer.class,
                    column.toUpperCase()
            );
            if (count != null && count > 0) {
                return true;
            }
        } catch (DataAccessException ignored) {
            // Fall back to a direct column query if metadata lookup is unavailable.
        }

        try {
            jdbc.query("SELECT " + column + " FROM users WHERE 1=0", rs -> null);
            return true;
        } catch (BadSqlGrammarException ignored) {
            return false;
        }
    }

    private int createCompany(int userId, CompanyRegisterRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO companies (user_id, name, industry) VALUES (?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS
            );
            ps.setInt(1, userId);
            ps.setString(2, request.companyName().trim());
            ps.setString(3, request.industry());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ApiException(500, "Could not create company profile.");
        }
        return key.intValue();
    }

    private UserProfileDto buildProfile(int userId) {
        Map<String, Object> user;
        try {
            user = jdbc.queryForMap(
                    "SELECT id, email, role FROM users WHERE id = ?",
                    userId
            );
        } catch (EmptyResultDataAccessException ex) {
            throw new ApiException(404, "User account not found.");
        }

        UserRole role = UserRole.valueOf((String) user.get("role"));

        if (role == UserRole.COMPANY) {
            try {
                return jdbc.queryForObject(
                        """
                        SELECT u.id AS user_id, u.email, c.id AS company_id, c.name AS company_name
                        FROM users u
                        JOIN companies c ON c.user_id = u.id
                        WHERE u.id = ?
                        """,
                        (rs, rowNum) -> new UserProfileDto(
                                rs.getInt("user_id"),
                                rs.getString("email"),
                                UserRole.COMPANY,
                                rs.getString("company_name"),
                                rs.getInt("company_id"),
                                rs.getString("company_name"),
                                null,
                                null,
                                null,
                                null,
                                null
                        ),
                        userId
                );
            } catch (EmptyResultDataAccessException ex) {
                throw new ApiException(500, "Company profile is missing for this account. Please contact support.");
            }
        }

        try {
            return jdbc.queryForObject(
                    """
                    SELECT u.id AS user_id, u.email, ca.id AS candidate_id,
                           ca.first_name, ca.last_name, ca.phone, ca.resume_url
                    FROM users u
                    JOIN candidates ca ON ca.user_id = u.id
                    WHERE u.id = ?
                    """,
                    (rs, rowNum) -> new UserProfileDto(
                            rs.getInt("user_id"),
                            rs.getString("email"),
                            UserRole.CANDIDATE,
                            rs.getString("first_name") + " " + rs.getString("last_name"),
                            null,
                            null,
                            rs.getInt("candidate_id"),
                            rs.getString("first_name"),
                            rs.getString("last_name"),
                            rs.getString("phone"),
                            rs.getString("resume_url")
                    ),
                    userId
            );
        } catch (EmptyResultDataAccessException ex) {
            throw new ApiException(500, "Candidate profile is missing for this account. Please contact support.");
        }
    }
}
