package com.recruitment.service;

import com.recruitment.auth.UserRole;
import com.recruitment.dto.ContactRequest;
import com.recruitment.dto.InboxItemDto;
import com.recruitment.dto.MessageDto;
import com.recruitment.dto.OutreachResponse;
import com.recruitment.exception.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class MessageService {

    private final JdbcTemplate jdbc;
    private final NotificationService notificationService;

    public MessageService(JdbcTemplate jdbc, NotificationService notificationService) {
        this.jdbc = jdbc;
        this.notificationService = notificationService;
    }

    public List<InboxItemDto> findInboxForCandidate(int candidateId) {
        List<InboxItemDto> items = new ArrayList<>();
        items.addAll(findApplicationInboxForCandidate(candidateId));
        items.addAll(findConversationInboxForCandidate(candidateId));
        items.sort(Comparator.comparing(InboxItemDto::lastMessageAt).reversed());
        return items;
    }

    public List<InboxItemDto> findInboxForCompany(int companyId) {
        List<InboxItemDto> items = new ArrayList<>();
        items.addAll(findApplicationInboxForCompany(companyId));
        items.addAll(findConversationInboxForCompany(companyId));
        items.sort(Comparator.comparing(InboxItemDto::lastMessageAt).reversed());
        return items;
    }

    private List<InboxItemDto> findApplicationInboxForCandidate(int candidateId) {
        return jdbc.query(
                """
                SELECT a.id AS application_id,
                       NULL AS conversation_id,
                       j.title AS job_title,
                       c.name AS contact_name,
                       (SELECT m.message FROM messages m WHERE m.application_id = a.id ORDER BY m.sent_at DESC LIMIT 1) AS last_message,
                       (SELECT m.sent_at FROM messages m WHERE m.application_id = a.id ORDER BY m.sent_at DESC LIMIT 1) AS last_message_at,
                       (SELECT COUNT(*) FROM messages m WHERE m.application_id = a.id) AS message_count
                FROM applications a
                JOIN jobs j ON a.job_id = j.id
                JOIN companies c ON j.company_id = c.id
                WHERE a.candidate_id = ?
                  AND EXISTS (SELECT 1 FROM messages m WHERE m.application_id = a.id)
                """,
                this::mapInboxItem,
                candidateId
        );
    }

    private List<InboxItemDto> findConversationInboxForCandidate(int candidateId) {
        return jdbc.query(
                """
                SELECT NULL AS application_id,
                       conv.id AS conversation_id,
                       'Direct message' AS job_title,
                       c.name AS contact_name,
                       (SELECT m.message FROM messages m WHERE m.conversation_id = conv.id ORDER BY m.sent_at DESC LIMIT 1) AS last_message,
                       (SELECT m.sent_at FROM messages m WHERE m.conversation_id = conv.id ORDER BY m.sent_at DESC LIMIT 1) AS last_message_at,
                       (SELECT COUNT(*) FROM messages m WHERE m.conversation_id = conv.id) AS message_count
                FROM conversations conv
                JOIN companies c ON conv.company_id = c.id
                WHERE conv.candidate_id = ?
                  AND EXISTS (SELECT 1 FROM messages m WHERE m.conversation_id = conv.id)
                """,
                this::mapInboxItem,
                candidateId
        );
    }

    private List<InboxItemDto> findApplicationInboxForCompany(int companyId) {
        return jdbc.query(
                """
                SELECT a.id AS application_id,
                       NULL AS conversation_id,
                       j.title AS job_title,
                       CONCAT(ca.first_name, ' ', ca.last_name) AS contact_name,
                       (SELECT m.message FROM messages m WHERE m.application_id = a.id ORDER BY m.sent_at DESC LIMIT 1) AS last_message,
                       (SELECT m.sent_at FROM messages m WHERE m.application_id = a.id ORDER BY m.sent_at DESC LIMIT 1) AS last_message_at,
                       (SELECT COUNT(*) FROM messages m WHERE m.application_id = a.id) AS message_count
                FROM applications a
                JOIN jobs j ON a.job_id = j.id
                JOIN candidates ca ON a.candidate_id = ca.id
                WHERE j.company_id = ?
                  AND EXISTS (SELECT 1 FROM messages m WHERE m.application_id = a.id)
                """,
                this::mapInboxItem,
                companyId
        );
    }

    private List<InboxItemDto> findConversationInboxForCompany(int companyId) {
        return jdbc.query(
                """
                SELECT NULL AS application_id,
                       conv.id AS conversation_id,
                       'Direct outreach' AS job_title,
                       CONCAT(ca.first_name, ' ', ca.last_name) AS contact_name,
                       (SELECT m.message FROM messages m WHERE m.conversation_id = conv.id ORDER BY m.sent_at DESC LIMIT 1) AS last_message,
                       (SELECT m.sent_at FROM messages m WHERE m.conversation_id = conv.id ORDER BY m.sent_at DESC LIMIT 1) AS last_message_at,
                       (SELECT COUNT(*) FROM messages m WHERE m.conversation_id = conv.id) AS message_count
                FROM conversations conv
                JOIN candidates ca ON conv.candidate_id = ca.id
                WHERE conv.company_id = ?
                  AND EXISTS (SELECT 1 FROM messages m WHERE m.conversation_id = conv.id)
                """,
                this::mapInboxItem,
                companyId
        );
    }

    private InboxItemDto mapInboxItem(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new InboxItemDto(
                (Integer) rs.getObject("application_id"),
                (Integer) rs.getObject("conversation_id"),
                rs.getString("job_title"),
                rs.getString("contact_name"),
                rs.getString("last_message"),
                formatDateTime(rs.getTimestamp("last_message_at")),
                rs.getInt("message_count")
        );
    }

    public int messageCountForCandidate(int candidateId) {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM messages m
                LEFT JOIN applications a ON m.application_id = a.id
                LEFT JOIN conversations conv ON m.conversation_id = conv.id
                WHERE (a.candidate_id = ? OR conv.candidate_id = ?)
                  AND m.sender_role != 'CANDIDATE'
                """,
                Integer.class,
                candidateId,
                candidateId
        );
        return count != null ? count : 0;
    }

    public int messageCountForCompany(int companyId) {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM messages m
                LEFT JOIN applications a ON m.application_id = a.id
                LEFT JOIN jobs j ON a.job_id = j.id
                LEFT JOIN conversations conv ON m.conversation_id = conv.id
                WHERE (j.company_id = ? OR conv.company_id = ?)
                  AND m.sender_role != 'COMPANY'
                """,
                Integer.class,
                companyId,
                companyId
        );
        return count != null ? count : 0;
    }

    public List<MessageDto> findByApplicationId(int applicationId, Integer candidateId, Integer companyId) {
        verifyApplicationAccess(applicationId, candidateId, companyId);
        return queryMessages("m.application_id = ?", applicationId);
    }

    public List<MessageDto> findByConversationId(int conversationId, Integer candidateId, Integer companyId) {
        verifyConversationAccess(conversationId, candidateId, companyId);
        return queryMessages("m.conversation_id = ?", conversationId);
    }

    private List<MessageDto> queryMessages(String whereClause, int id) {
        String sql = """
                SELECT m.id, m.application_id, m.conversation_id,
                       COALESCE(j.title, 'Direct message') AS job_title,
                       c.name AS company_name,
                       m.sender_role, m.message, m.sent_at,
                       CASE
                           WHEN m.sender_role = 'COMPANY' THEN co.name
                           ELSE CONCAT(ca.first_name, ' ', ca.last_name)
                       END AS sender_name
                FROM messages m
                LEFT JOIN applications a ON m.application_id = a.id
                LEFT JOIN jobs j ON a.job_id = j.id
                LEFT JOIN conversations conv ON m.conversation_id = conv.id
                LEFT JOIN companies c ON c.id = COALESCE(j.company_id, conv.company_id)
                LEFT JOIN candidates ca ON ca.id = COALESCE(a.candidate_id, conv.candidate_id)
                LEFT JOIN users u ON m.sender_user_id = u.id
                LEFT JOIN companies co ON co.user_id = u.id AND m.sender_role = 'COMPANY'
                WHERE %s ORDER BY m.sent_at ASC
                """.formatted(whereClause);
        return jdbc.query(
                sql,
                (rs, rowNum) -> new MessageDto(
                        rs.getInt("id"),
                        (Integer) rs.getObject("application_id"),
                        (Integer) rs.getObject("conversation_id"),
                        rs.getString("job_title"),
                        rs.getString("company_name"),
                        rs.getString("sender_name"),
                        rs.getString("sender_role"),
                        rs.getString("message"),
                        formatDateTime(rs.getTimestamp("sent_at"))
                ),
                id
        );
    }

    // --- Send messages ---

    // Send a message within an application thread (candidate or company)
    public void sendApplicationMessage(
            int applicationId,
            int senderUserId,
            UserRole senderRole,
            Integer candidateId,
            Integer companyId,
            ContactRequest request
    ) {
        verifyApplicationAccess(applicationId, candidateId, companyId);

        jdbc.update(
                "INSERT INTO messages (application_id, sender_user_id, sender_role, message) VALUES (?, ?, ?, ?)",
                applicationId,
                senderUserId,
                senderRole.name(),
                request.message().trim()
        );

        notifyApplicationMessage(applicationId, senderRole, request.message().trim());
    }

    // Send an outreach message from a company to a candidate
    public OutreachResponse sendOutreachToCandidate(
            int companyId,
            int companyUserId,
            int candidateId,
            ContactRequest request
    ) {
        Integer candidateExists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM candidates WHERE id = ?",
                Integer.class,
                candidateId
        );
        if (candidateExists == null || candidateExists == 0) {
            throw new ApiException(404, "Candidate not found.");
        }

        int conversationId = findOrCreateConversation(companyId, candidateId);

        jdbc.update(
                "INSERT INTO messages (conversation_id, sender_user_id, sender_role, message) VALUES (?, ?, ?, ?)",
                conversationId,
                companyUserId,
                UserRole.COMPANY.name(),
                request.message().trim()
        );

        notifyOutreachMessage(conversationId, request.message().trim());

        return new OutreachResponse(conversationId, "Message sent to candidate.");
    }

    // Send a message within an existing conversation (candidate or company)
    public void sendConversationMessage(
            int conversationId,
            int senderUserId,
            UserRole senderRole,
            Integer candidateId,
            Integer companyId,
            ContactRequest request
    ) {
        verifyConversationAccess(conversationId, candidateId, companyId);

        jdbc.update(
                "INSERT INTO messages (conversation_id, sender_user_id, sender_role, message) VALUES (?, ?, ?, ?)",
                conversationId,
                senderUserId,
                senderRole.name(),
                request.message().trim()
        );

        notifyOutreachMessage(conversationId, request.message().trim());
    }

    // --- Private helpers ---

    private int findOrCreateConversation(int companyId, int candidateId) {
        List<Integer> existing = jdbc.query(
                "SELECT id FROM conversations WHERE company_id = ? AND candidate_id = ?",
                (rs, rowNum) -> rs.getInt("id"),
                companyId,
                candidateId
        );
        if (!existing.isEmpty()) {
            return existing.get(0);
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
            throw new ApiException(500, "Could not start conversation.");
        }
        return key.intValue();
    }

    private void notifyApplicationMessage(int applicationId, UserRole senderRole, String message) {
        try {
            Map<String, Object> details = jdbc.queryForMap(
                    """
                    SELECT j.title AS job_title, c.name AS company_name,
                           CONCAT(ca.first_name, ' ', ca.last_name) AS candidate_name,
                           c.user_id AS company_user_id, ca.user_id AS candidate_user_id
                    FROM applications a
                    JOIN jobs j ON a.job_id = j.id
                    JOIN companies c ON j.company_id = c.id
                    JOIN candidates ca ON a.candidate_id = ca.id
                    WHERE a.id = ?
                    """,
                    applicationId
            );

            String preview = truncate(message);
            String jobTitle = (String) details.get("job_title");

            if (senderRole == UserRole.COMPANY) {
                notificationService.notify(
                        ((Number) details.get("candidate_user_id")).intValue(),
                        "NEW_MESSAGE",
                        "New message from " + details.get("company_name"),
                        preview + " — regarding " + jobTitle,
                        applicationId
                );
            } else {
                notificationService.notify(
                        ((Number) details.get("company_user_id")).intValue(),
                        "NEW_MESSAGE",
                        "Reply from " + details.get("candidate_name"),
                        preview + " — regarding " + jobTitle,
                        applicationId
                );
            }
        } catch (Exception ignored) {
            // Notifications should not block messaging
        }
    }

    private void notifyOutreachMessage(int conversationId, String message) {
        try {
            Map<String, Object> details = jdbc.queryForMap(
                    """
                    SELECT c.name AS company_name,
                           CONCAT(ca.first_name, ' ', ca.last_name) AS candidate_name,
                           c.user_id AS company_user_id,
                           ca.user_id AS candidate_user_id,
                           m.sender_role
                    FROM conversations conv
                    JOIN companies c ON conv.company_id = c.id
                    JOIN candidates ca ON conv.candidate_id = ca.id
                    JOIN messages m ON m.conversation_id = conv.id
                    WHERE conv.id = ?
                    ORDER BY m.sent_at DESC
                    LIMIT 1
                    """,
                    conversationId
            );

            String preview = truncate(message);
            String senderRole = (String) details.get("sender_role");

            if (UserRole.COMPANY.name().equals(senderRole)) {
                notificationService.notify(
                        ((Number) details.get("candidate_user_id")).intValue(),
                        "OUTREACH_MESSAGE",
                        "Message from " + details.get("company_name"),
                        preview,
                        conversationId
                );
            } else {
                notificationService.notify(
                        ((Number) details.get("company_user_id")).intValue(),
                        "OUTREACH_MESSAGE",
                        "Reply from " + details.get("candidate_name"),
                        preview,
                        conversationId
                );
            }
        } catch (Exception ignored) {
            // Notifications should not block messaging
        }
    }

    private void verifyApplicationAccess(int applicationId, Integer candidateId, Integer companyId) {
        if (candidateId != null) {
            Integer owned = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM applications WHERE id = ? AND candidate_id = ?",
                    Integer.class,
                    applicationId,
                    candidateId
            );
            if (owned != null && owned > 0) {
                return;
            }
        }

        if (companyId != null) {
            Integer owned = jdbc.queryForObject(
                    """
                    SELECT COUNT(*) FROM applications a
                    JOIN jobs j ON a.job_id = j.id
                    WHERE a.id = ? AND j.company_id = ?
                    """,
                    Integer.class,
                    applicationId,
                    companyId
            );
            if (owned != null && owned > 0) {
                return;
            }
        }

        throw new ApiException(403, "You do not have access to this conversation.");
    }

    private void verifyConversationAccess(int conversationId, Integer candidateId, Integer companyId) {
        if (candidateId != null) {
            Integer owned = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM conversations WHERE id = ? AND candidate_id = ?",
                    Integer.class,
                    conversationId,
                    candidateId
            );
            if (owned != null && owned > 0) {
                return;
            }
        }

        if (companyId != null) {
            Integer owned = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM conversations WHERE id = ? AND company_id = ?",
                    Integer.class,
                    conversationId,
                    companyId
            );
            if (owned != null && owned > 0) {
                return;
            }
        }

        throw new ApiException(403, "You do not have access to this conversation.");
    }

    private String truncate(String message) {
        return message.length() > 80 ? message.substring(0, 80) + "…" : message;
    }

    private String formatDateTime(java.sql.Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }
        return timestamp.toLocalDateTime().toString().replace('T', ' ');
    }
}
