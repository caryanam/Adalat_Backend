package com.adalat.dto;

import com.adalat.enums.CallType;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallInitiateDTO {
    private Long consultationRequestId;
    private CallType callType;
}
