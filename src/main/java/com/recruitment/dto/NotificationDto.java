package com.recruitment.dto;

public record NotificationDto(
        int id,
        String type,
        String title,
        String body,
        Integer relatedId,
        boolean read,
        String createdAt
) {}
