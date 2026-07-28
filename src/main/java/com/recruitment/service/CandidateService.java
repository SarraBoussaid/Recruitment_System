package com.recruitment.service;

import com.recruitment.dto.UserProfileDto;
import com.recruitment.validation.PhoneValidator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CandidateService {

    private final JdbcTemplate jdbc;
    private final AuthService authService;

    public CandidateService(JdbcTemplate jdbc, AuthService authService) {
        this.jdbc = jdbc;
        this.authService = authService;
    }

    public UserProfileDto updateProfile(int candidateId, int userId, String phone) {
        String normalizedPhone = phone != null && !phone.isBlank()
                ? PhoneValidator.normalize(phone)
                : null;

        jdbc.update("UPDATE candidates SET phone = ? WHERE id = ?", normalizedPhone, candidateId);
        return authService.getCurrentUserById(userId);
    }
}
