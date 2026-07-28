package com.recruitment.dto;

import com.recruitment.validation.TunisianPhone;
import jakarta.validation.constraints.NotNull;

public record ApplicationRequest(
        @NotNull Integer jobId,
        @TunisianPhone String phone,
        String resumeUrl
) {}
