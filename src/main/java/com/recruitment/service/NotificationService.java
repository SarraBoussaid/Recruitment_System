package com.recruitment.service;

import com.recruitment.dto.NotificationDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private final JdbcTemplate jdbc;

    public NotificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void notify(int userId, String type, String title, String body, Integer relatedId) {
        jdbc.update(
                "INSERT INTO notifications (user_id, type, title, body, related_id) VALUES (?, ?, ?, ?, ?)",
                userId,
                type,
                title,
                body,
                relatedId
        );
    }

    public List<NotificationDto> findByUserId(int userId) {
        return jdbc.query(
                """
                SELECT id, type, title, body, related_id, read_at, created_at
                FROM notifications
                WHERE user_id = ?
                ORDER BY created_at DESC
                LIMIT 50
                """,
                (rs, rowNum) -> new NotificationDto(
                        rs.getInt("id"),
                        rs.getString("type"),
                        rs.getString("title"),
                        rs.getString("body"),
                        rs.getObject("related_id") != null ? rs.getInt("related_id") : null,
                        rs.getTimestamp("read_at") != null,
                        formatDateTime(rs.getTimestamp("created_at"))
                ),
                userId
        );
    }

    public int unreadCount(int userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND read_at IS NULL",
                Integer.class,
                userId
        );
        return count != null ? count : 0;
    }

    public void markRead(int id, int userId) {
        jdbc.update(
                "UPDATE notifications SET read_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ?",
                id,
                userId
        );
    }

    public void markAllRead(int userId) {
        jdbc.update(
                "UPDATE notifications SET read_at = CURRENT_TIMESTAMP WHERE user_id = ? AND read_at IS NULL",
                userId
        );
    }

    public Integer findCompanyUserIdByJobId(int jobId) {
        return jdbc.queryForObject(
                """
                SELECT c.user_id FROM jobs j
                JOIN companies c ON j.company_id = c.id
                WHERE j.id = ?
                """,
                Integer.class,
                jobId
        );
    }

    public Integer findCandidateUserIdByApplicationId(int applicationId) {
        return jdbc.queryForObject(
                """
                SELECT ca.user_id FROM applications a
                JOIN candidates ca ON a.candidate_id = ca.id
                WHERE a.id = ?
                """,
                Integer.class,
                applicationId
        );
    }

    private String formatDateTime(java.sql.Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }
        return timestamp.toLocalDateTime().toString().replace('T', ' ');
    }
}
