package com.adalat.dto.ai;

import com.adalat.enums.PracticeArea;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class LawyerInfoDTO {
    private Long lawyerId;
    private String fullName;
    private String location;
    private Set<PracticeArea> practiceAreas;
    private Double rating;
    private Integer consultationFee;
}
