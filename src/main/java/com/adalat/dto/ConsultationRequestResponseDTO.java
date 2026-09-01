package com.adalat.dto;

import com.adalat.enums.ConsultationRequestStatus;
import com.adalat.enums.LegalCategory;
import com.adalat.enums.PracticeArea;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConsultationRequestResponseDTO {

    private Long id;
    private Long sessionId;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private String customerMobileNumber;
    private Long lawyerId;
    private String lawyerName;
    private String lawyerLocation;
    private Integer lawyerRate;
    private LegalCategory category;
    private String categoryDisplayName;
    private PracticeArea practiceArea;
    private String caseSummary;
    private ConsultationRequestStatus status;
    private String lawyerNotes;
    private String nextStepInstruction;
    private LocalDateTime scheduledAt;
    private java.math.BigDecimal paymentAmount;
    private com.adalat.enums.PaymentStatus paymentStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
