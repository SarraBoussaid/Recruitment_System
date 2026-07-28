package com.recruitment.controller;

import com.recruitment.dto.JobCreateRequest;
import com.recruitment.dto.JobDto;
import com.recruitment.dto.UserProfileDto;
import com.recruitment.service.AuthService;
import com.recruitment.service.JobService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Job-related endpoints. Company users can create, delete or close their postings.
 */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;
    private final AuthService authService;

    public JobController(JobService jobService, AuthService authService) {
        this.jobService = jobService;
        this.authService = authService;
    }

    @GetMapping
    public List<JobDto> listJobs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String type
    ) {
        return jobService.findJobs(keyword, location, type);
    }

    @GetMapping("/{id}")
    public JobDto getJob(@PathVariable int id) {
        JobDto job = jobService.findById(id);
        if (job == null) {
            throw new com.recruitment.exception.ApiException(404, "Job not found.");
        }
        return job;
    }

    @PostMapping
    public ResponseEntity<JobDto> createJob(@Valid @RequestBody JobCreateRequest request, HttpSession session) {
        UserProfileDto company = authService.requireCompany(session);
        JobDto created = jobService.createJob(request, company.companyId());
        return ResponseEntity.status(201).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable int id, HttpSession session) {
        UserProfileDto company = authService.requireCompany(session);
        jobService.deleteJob(id, company.companyId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unavailable")
    public ResponseEntity<Void> markJobUnavailable(@PathVariable int id, HttpSession session) {
        UserProfileDto company = authService.requireCompany(session);
        jobService.markUnavailable(id, company.companyId());
        return ResponseEntity.noContent().build();
    }
}
