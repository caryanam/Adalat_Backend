package com.adalat.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class NextStepRequestDTO {
    @NotBlank(message = "Action must be specified.")
    @Pattern(regexp = "CONNECT_LAWYER|AI_ONLY", message = "Action must be CONNECT_LAWYER or AI_ONLY.")
    private String action;
}
