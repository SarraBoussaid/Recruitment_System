package com.recruitment.dto;

import com.recruitment.validation.TunisianPhone;

public record CandidateProfileUpdateRequest(
        @TunisianPhone String phone
) {}
