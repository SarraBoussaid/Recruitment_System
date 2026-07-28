package com.recruitment.dto;

public record MessageDto(
        int id,
        Integer applicationId,
        Integer conversationId,
        String jobTitle,
        String companyName,
        String senderName,
        String senderRole,
        String message,
        String sentAt
) {}
