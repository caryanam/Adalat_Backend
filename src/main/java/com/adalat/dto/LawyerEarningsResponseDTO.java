package com.adalat.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LawyerEarningsResponseDTO {
    private String totalEarnings;
    private BigDecimal totalEarningsNum;
    private String todayEarnings;
    private BigDecimal todayEarningsNum;
    private Integer completedConsultations;
    private String lawyerUpiId;
    private String lawyerName;
    private List<LawyerTransactionDTO> transactions;
}
