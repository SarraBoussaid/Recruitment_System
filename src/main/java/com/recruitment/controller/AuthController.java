package com.recruitment.controller;

import com.recruitment.dto.CandidateRegisterRequest;
import com.recruitment.dto.CompanyRegisterRequest;
import com.recruitment.dto.LoginRequest;
import com.recruitment.dto.UserProfileDto;
import com.recruitment.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register/candidate")
    @ResponseStatus(HttpStatus.CREATED)
    public UserProfileDto registerCandidate(
            @Valid @RequestBody CandidateRegisterRequest request,
            HttpSession session
    ) {
        return authService.registerCandidate(request, session);
    }

    @PostMapping("/register/company")
    @ResponseStatus(HttpStatus.CREATED)
    public UserProfileDto registerCompany(
            @Valid @RequestBody CompanyRegisterRequest request,
            HttpSession session
    ) {
        return authService.registerCompany(request, session);
    }

    @PostMapping("/login")
    public UserProfileDto login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        return authService.login(request, session);
    }

    @PostMapping("/logout")
    public void logout(HttpSession session) {
        authService.logout(session);
    }

    @GetMapping("/me")
    public UserProfileDto me(HttpSession session) {
        return authService.getCurrentUser(session);
    }
}
