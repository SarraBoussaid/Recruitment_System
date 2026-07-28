package com.recruitment.controller;

import com.recruitment.dto.NotificationDto;
import com.recruitment.dto.UserProfileDto;
import com.recruitment.service.AuthService;
import com.recruitment.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthService authService;

    public NotificationController(NotificationService notificationService, AuthService authService) {
        this.notificationService = notificationService;
        this.authService = authService;
    }

    @GetMapping
    public List<NotificationDto> list(HttpSession session) {
        UserProfileDto user = authService.getCurrentUser(session);
        return notificationService.findByUserId(user.userId());
    }

    @GetMapping("/unread-count")
    public Map<String, Integer> unreadCount(HttpSession session) {
        UserProfileDto user = authService.getCurrentUser(session);
        return Map.of("count", notificationService.unreadCount(user.userId()));
    }

    @PatchMapping("/{id}/read")
    public Map<String, String> markRead(@PathVariable int id, HttpSession session) {
        UserProfileDto user = authService.getCurrentUser(session);
        notificationService.markRead(id, user.userId());
        return Map.of("message", "Notification marked as read.");
    }

    @PatchMapping("/read-all")
    public Map<String, String> markAllRead(HttpSession session) {
        UserProfileDto user = authService.getCurrentUser(session);
        notificationService.markAllRead(user.userId());
        return Map.of("message", "All notifications marked as read.");
    }
}
