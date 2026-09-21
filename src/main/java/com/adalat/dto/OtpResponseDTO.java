package com.adalat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OtpResponseDTO {
    private Boolean success;
    private String message;
    private Boolean emailVerified;
    private Long resendAvailableAfterSeconds;
    private Long otpExpiresAfterSeconds;
    private Long retryAfterSeconds;
}
