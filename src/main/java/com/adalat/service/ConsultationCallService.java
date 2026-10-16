package com.adalat.service;

import com.adalat.dto.ConsultationCallDTO;
import com.adalat.enums.CallType;

import java.util.List;

public interface ConsultationCallService {

    ConsultationCallDTO initiateCall(Long consultationRequestId, Long callerId, String callerRole, CallType callType);

    ConsultationCallDTO respondToCall(Long callId, Long responderId, String responderRole, String response);

    ConsultationCallDTO endCall(Long callId, Long endedById, String reason);

    List<ConsultationCallDTO> getCallHistory(Long consultationRequestId, Long userId, String role);

    ConsultationCallDTO getActiveCall(Long userId);

    ConsultationCallDTO getCallById(Long callId, Long userId, String role);

    void handleUserDisconnect(Long userId, String role);
}
