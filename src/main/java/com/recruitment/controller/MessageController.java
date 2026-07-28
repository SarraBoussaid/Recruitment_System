package com.recruitment.controller;

import com.recruitment.auth.UserRole;
import com.recruitment.dto.ContactRequest;
import com.recruitment.dto.InboxItemDto;
import com.recruitment.dto.MessageDto;
import com.recruitment.dto.OutreachResponse;
import com.recruitment.dto.UserProfileDto;
import com.recruitment.service.AuthService;
import com.recruitment.service.MessageService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;
    private final AuthService authService;

    public MessageController(MessageService messageService, AuthService authService) {
        this.messageService = messageService;
        this.authService = authService;
    }

    @GetMapping("/inbox")
    public List<InboxItemDto> inbox(HttpSession session) {
        UserProfileDto user = authService.getCurrentUser(session);
        if (user.role() == UserRole.CANDIDATE) {
            return messageService.findInboxForCandidate(user.candidateId());
        }
        if (user.role() == UserRole.COMPANY) {
            return messageService.findInboxForCompany(user.companyId());
        }
        throw new com.recruitment.exception.ApiException(403, "Account required.");
    }

    @GetMapping("/application/{applicationId}")
    public List<MessageDto> applicationThread(
            @PathVariable int applicationId,
            HttpSession session
    ) {
        UserProfileDto user = authService.getCurrentUser(session);
        return messageService.findByApplicationId(applicationId, user.candidateId(), user.companyId());
    }

    @GetMapping("/conversation/{conversationId}")
    public List<MessageDto> conversationThread(
            @PathVariable int conversationId,
            HttpSession session
    ) {
        UserProfileDto user = authService.getCurrentUser(session);
        return messageService.findByConversationId(conversationId, user.candidateId(), user.companyId());
    }

    @PostMapping("/application/{applicationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> sendApplicationMessage(
            @PathVariable int applicationId,
            @Valid @RequestBody ContactRequest request,
            HttpSession session
    ) {
        UserProfileDto user = authService.getCurrentUser(session);
        messageService.sendApplicationMessage(
                applicationId,
                user.userId(),
                user.role(),
                user.candidateId(),
                user.companyId(),
                request
        );
        return Map.of("message", "Message sent.");
    }

    @PostMapping("/conversation/{conversationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> sendConversationMessage(
            @PathVariable int conversationId,
            @Valid @RequestBody ContactRequest request,
            HttpSession session
    ) {
        UserProfileDto user = authService.getCurrentUser(session);
        messageService.sendConversationMessage(
                conversationId,
                user.userId(),
                user.role(),
                user.candidateId(),
                user.companyId(),
                request
        );
        return Map.of("message", "Message sent.");
    }

    @PostMapping("/candidate/{candidateId}")
    @ResponseStatus(HttpStatus.CREATED)
    public OutreachResponse contactCandidate(
            @PathVariable int candidateId,
            @Valid @RequestBody ContactRequest request,
            HttpSession session
    ) {
        UserProfileDto company = authService.requireCompany(session);
        return messageService.sendOutreachToCandidate(
                company.companyId(),
                company.userId(),
                candidateId,
                request
        );
    }
}
