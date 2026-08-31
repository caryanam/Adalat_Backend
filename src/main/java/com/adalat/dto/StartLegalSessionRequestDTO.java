package com.adalat.dto;

import com.adalat.enums.LegalCategory;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StartLegalSessionRequestDTO {

    private LegalCategory selectedCategory;

    private String initialProblemText;
}
