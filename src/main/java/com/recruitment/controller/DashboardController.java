package com.recruitment.controller;

import com.recruitment.dto.CandidateDashboardDto;
import com.recruitment.dto.CandidateProfileUpdateRequest;
import com.recruitment.dto.CompanyDashboardDto;
import com.recruitment.dto.UserProfileDto;
import com.recruitment.service.CandidateService;
import com.recruitment.service.DashboardService;
import com.recruitment.service.ResumeService;
import com.recruitment.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboardService;
    private final ResumeService resumeService;
    private final CandidateService candidateService;
    private final AuthService authService;

    public DashboardController(
            DashboardService dashboardService,
            ResumeService resumeService,
            CandidateService candidateService,
            AuthService authService
    ) {
        this.dashboardService = dashboardService;
        this.resumeService = resumeService;
        this.candidateService = candidateService;
        this.authService = authService;
    }

    @GetMapping("/dashboard/candidate")
    public CandidateDashboardDto candidateDashboard(HttpSession session) {
        UserProfileDto candidate = authService.requireCandidate(session);
        return dashboardService.getCandidateDashboard(candidate);
    }

    @GetMapping("/dashboard/company")
    public CompanyDashboardDto companyDashboard(HttpSession session) {
        UserProfileDto company = authService.requireCompany(session);
        return dashboardService.getCompanyDashboard(company);
    }

    @PostMapping(value = "/candidates/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> uploadResume(
            @RequestParam("file") MultipartFile file,
            HttpSession session
    ) {
        UserProfileDto candidate = authService.requireCandidate(session);
        return resumeService.uploadResume(file, candidate.candidateId());
    }

    @PatchMapping("/candidates/profile")
    public UserProfileDto updateProfile(
            @Valid @RequestBody CandidateProfileUpdateRequest request,
            HttpSession session
    ) {
        UserProfileDto candidate = authService.requireCandidate(session);
        return candidateService.updateProfile(
                candidate.candidateId(),
                candidate.userId(),
                request.phone()
        );
    }
}
