package com.recruitment.controller;

import com.recruitment.dto.ApplicationRequest;
import com.recruitment.dto.ApplicationResponse;
import com.recruitment.dto.CandidateApplicationDto;
import com.recruitment.dto.ContactRequest;
import com.recruitment.dto.ManagedApplicationDto;
import com.recruitment.dto.StatusUpdateRequest;
import com.recruitment.dto.UserProfileDto;
import com.recruitment.service.ApplicationService;
import com.recruitment.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final AuthService authService;

    public ApplicationController(ApplicationService applicationService, AuthService authService) {
        this.applicationService = applicationService;
        this.authService = authService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse submit(
            @Valid @RequestBody ApplicationRequest request,
            HttpSession session
    ) {
        UserProfileDto candidate = authService.requireCandidate(session);
        return applicationService.submit(request, candidate);
    }

    @GetMapping("/me")
    public List<CandidateApplicationDto> myApplications(HttpSession session) {
        UserProfileDto candidate = authService.requireCandidate(session);
        return applicationService.findByCandidateId(candidate.candidateId());
    }

    @GetMapping("/manage")
    public List<ManagedApplicationDto> manageApplications(HttpSession session) {
        UserProfileDto company = authService.requireCompany(session);
        return applicationService.findAllForCompany(company.companyId());
    }

    @PatchMapping("/{id}/status")
    public Map<String, String> updateStatus(
            @PathVariable int id,
            @Valid @RequestBody StatusUpdateRequest request,
            HttpSession session
    ) {
        UserProfileDto company = authService.requireCompany(session);
        applicationService.updateStatus(id, request.status(), company.companyId());
        return Map.of("message", "Application status updated.");
    }

    @PostMapping("/{id}/contact")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> contactCandidate(
            @PathVariable int id,
            @Valid @RequestBody ContactRequest request,
            HttpSession session
    ) {
        UserProfileDto company = authService.requireCompany(session);
        applicationService.contactCandidate(id, company.companyId(), company.userId(), request);
        return Map.of("message", "Message sent to candidate.");
    }
}
