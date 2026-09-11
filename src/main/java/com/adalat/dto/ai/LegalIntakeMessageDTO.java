package com.adalat.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegalIntakeMessageDTO {
    private Long id;
    private String senderType;
    private String message;
    private LocalDateTime createdAt;
}
