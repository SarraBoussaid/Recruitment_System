package com.recruitment.dto;

public record InboxItemDto(
        Integer applicationId,
        Integer conversationId,
        String jobTitle,
        String contactName,
        String lastMessage,
        String lastMessageAt,
        int messageCount
) {}
