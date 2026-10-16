package com.adalat.serviceImpl;

import com.adalat.dto.ConsultationCallDTO;
import com.adalat.entity.ConsultationCall;
import com.adalat.entity.ConsultationRequest;
import com.adalat.enums.CallStatus;
import com.adalat.enums.CallType;
import com.adalat.exception.ResourceNotFoundException;
import com.adalat.repository.ConsultationCallRepository;
import com.adalat.repository.ConsultationRequestRepository;
import com.adalat.service.ConsultationCallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsultationCallServiceImpl implements ConsultationCallService {

    private final ConsultationCallRepository consultationCallRepository;
    private final ConsultationRequestRepository consultationRequestRepository;

    @Override
    @Transactional
    public ConsultationCallDTO initiateCall(Long consultationRequestId, Long callerId, String callerRole, CallType callType) {
        ConsultationRequest request = consultationRequestRepository.findById(consultationRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation request not found with ID: " + consultationRequestId));

        Long receiverId;
        String receiverRole;
        String callerName;
        String receiverName;

        String normalizedRole = callerRole.toUpperCase().replace("ROLE_", "");

        if ("CUSTOMER".equals(normalizedRole)) {
            if (!request.getCustomer().getCustomerId().equals(callerId)) {
                throw new SecurityException("Unauthorized: Customer is not a participant in this consultation request");
            }
            receiverId = request.getLawyer().getLawyerId();
            receiverRole = "LAWYER";
            callerName = request.getCustomer().getFullName();
            receiverName = request.getLawyer().getFullName();
        } else if ("LAWYER".equals(normalizedRole)) {
            if (!request.getLawyer().getLawyerId().equals(callerId)) {
                throw new SecurityException("Unauthorized: Lawyer is not a participant in this consultation request");
            }
            receiverId = request.getCustomer().getCustomerId();
            receiverRole = "CUSTOMER";
            callerName = request.getLawyer().getFullName();
            receiverName = request.getCustomer().getFullName();
        } else {
            throw new SecurityException("Unauthorized: Only customers and lawyers can initiate consultation calls");
        }

        // Clean up stale ringing/initiated calls (> 45 seconds)
        List<CallStatus> activeStatuses = List.of(CallStatus.INITIATED, CallStatus.RINGING, CallStatus.ACCEPTED);
        List<ConsultationCall> callerActive = consultationCallRepository.findActiveCallsForUser(callerId, activeStatuses);
        for (ConsultationCall c : callerActive) {
            if (c.getStatus() == CallStatus.INITIATED || c.getStatus() == CallStatus.RINGING) {
                if (c.getCreatedAt() != null && c.getCreatedAt().isBefore(LocalDateTime.now().minusSeconds(45))) {
                    c.setStatus(CallStatus.MISSED);
                    c.setEndedAt(LocalDateTime.now());
                    c.setEndReason("TIMEOUT");
                    c.setDurationSeconds(0);
                    consultationCallRepository.save(c);
                } else {
                    throw new IllegalStateException("CALLER_ALREADY_IN_CALL");
                }
            } else if (c.getStatus() == CallStatus.ACCEPTED) {
                throw new IllegalStateException("CALLER_ALREADY_IN_CALL");
            }
        }

        // Check if receiver is busy
        List<ConsultationCall> receiverActive = consultationCallRepository.findActiveCallsForUser(receiverId, activeStatuses);
        for (ConsultationCall c : receiverActive) {
            if (c.getStatus() == CallStatus.ACCEPTED || 
               (c.getStatus() == CallStatus.RINGING && c.getCreatedAt() != null && c.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(45)))) {
                throw new IllegalStateException("RECEIVER_BUSY");
            }
        }

        ConsultationCall call = ConsultationCall.builder()
                .consultationRequestId(consultationRequestId)
                .callerId(callerId)
                .callerRole(normalizedRole)
                .callerName(callerName)
                .receiverId(receiverId)
                .receiverRole(receiverRole)
                .receiverName(receiverName)
                .callType(callType != null ? callType : CallType.AUDIO)
                .status(CallStatus.RINGING)
                .startedAt(LocalDateTime.now())
                .durationSeconds(0)
                .build();

        ConsultationCall saved = consultationCallRepository.save(call);
        log.info("Initiated call: id={}, type={}, from {}:{} to {}:{}", 
                saved.getId(), saved.getCallType(), normalizedRole, callerId, receiverRole, receiverId);
        return mapToDTO(saved);
    }

    @Override
    @Transactional
    public ConsultationCallDTO respondToCall(Long callId, Long responderId, String responderRole, String response) {
        ConsultationCall call = consultationCallRepository.findById(callId)
                .orElseThrow(() -> new ResourceNotFoundException("Call not found with ID: " + callId));

        if (!call.getReceiverId().equals(responderId)) {
            throw new SecurityException("Unauthorized: Only the call recipient can respond to this call");
        }

        if (call.getStatus() != CallStatus.RINGING && call.getStatus() != CallStatus.INITIATED) {
            log.warn("Call {} already in status: {}", callId, call.getStatus());
            return mapToDTO(call);
        }

        LocalDateTime now = LocalDateTime.now();

        if ("ACCEPT".equalsIgnoreCase(response)) {
            call.setStatus(CallStatus.ACCEPTED);
            call.setAnsweredAt(now);
            log.info("Call {} ACCEPTED by recipient {}", callId, responderId);
        } else if ("REJECT".equalsIgnoreCase(response)) {
            call.setStatus(CallStatus.REJECTED);
            call.setEndedAt(now);
            call.setEndReason("REJECTED");
            call.setDurationSeconds(0);
            log.info("Call {} REJECTED by recipient {}", callId, responderId);
        } else if ("BUSY".equalsIgnoreCase(response)) {
            call.setStatus(CallStatus.BUSY);
            call.setEndedAt(now);
            call.setEndReason("BUSY");
            call.setDurationSeconds(0);
            log.info("Call {} marked BUSY by recipient {}", callId, responderId);
        } else if ("TIMEOUT".equalsIgnoreCase(response)) {
            call.setStatus(CallStatus.MISSED);
            call.setEndedAt(now);
            call.setEndReason("TIMEOUT");
            call.setDurationSeconds(0);
            log.info("Call {} TIMEOUT / MISSED", callId);
        } else {
            throw new IllegalArgumentException("Unknown response type: " + response);
        }

        ConsultationCall saved = consultationCallRepository.save(call);
        return mapToDTO(saved);
    }

    @Override
    @Transactional
    public ConsultationCallDTO endCall(Long callId, Long endedById, String reason) {
        ConsultationCall call = consultationCallRepository.findById(callId)
                .orElseThrow(() -> new ResourceNotFoundException("Call not found with ID: " + callId));

        if (!call.getCallerId().equals(endedById) && !call.getReceiverId().equals(endedById)) {
            throw new SecurityException("Unauthorized: Only call participants can end this call");
        }

        if (call.getStatus() == CallStatus.COMPLETED || call.getStatus() == CallStatus.REJECTED ||
            call.getStatus() == CallStatus.BUSY || call.getStatus() == CallStatus.MISSED) {
            return mapToDTO(call);
        }

        LocalDateTime now = LocalDateTime.now();
        call.setEndedAt(now);

        if (call.getStatus() == CallStatus.ACCEPTED && call.getAnsweredAt() != null) {
            long seconds = Duration.between(call.getAnsweredAt(), now).getSeconds();
            call.setDurationSeconds((int) Math.max(0, seconds));
            call.setStatus(CallStatus.COMPLETED);
            call.setEndReason(reason != null && !reason.isBlank() ? reason : "NORMAL");
        } else {
            call.setDurationSeconds(0);
            if (call.getCallerId().equals(endedById)) {
                call.setStatus(CallStatus.MISSED);
                call.setEndReason(reason != null && !reason.isBlank() ? reason : "CANCELLED_BY_CALLER");
            } else {
                call.setStatus(CallStatus.REJECTED);
                call.setEndReason(reason != null && !reason.isBlank() ? reason : "DECLINED_BY_RECEIVER");
            }
        }

        ConsultationCall saved = consultationCallRepository.save(call);
        log.info("Call {} ended: status={}, duration={}s, reason={}", 
                saved.getId(), saved.getStatus(), saved.getDurationSeconds(), saved.getEndReason());
        return mapToDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsultationCallDTO> getCallHistory(Long consultationRequestId, Long userId, String role) {
        ConsultationRequest request = consultationRequestRepository.findById(consultationRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation request not found with ID: " + consultationRequestId));

        String normalizedRole = role.toUpperCase().replace("ROLE_", "");
        if ("CUSTOMER".equals(normalizedRole) && !request.getCustomer().getCustomerId().equals(userId)) {
            throw new SecurityException("Unauthorized access to consultation call history");
        }
        if ("LAWYER".equals(normalizedRole) && !request.getLawyer().getLawyerId().equals(userId)) {
            throw new SecurityException("Unauthorized access to consultation call history");
        }

        return consultationCallRepository.findByConsultationRequestIdOrderByCreatedAtDesc(consultationRequestId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ConsultationCallDTO getActiveCall(Long userId) {
        List<CallStatus> active = List.of(CallStatus.RINGING, CallStatus.ACCEPTED);
        List<ConsultationCall> calls = consultationCallRepository.findActiveCallsForUser(userId, active);
        if (calls.isEmpty()) {
            return null;
        }
        return mapToDTO(calls.get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public ConsultationCallDTO getCallById(Long callId, Long userId, String role) {
        ConsultationCall call = consultationCallRepository.findById(callId)
                .orElseThrow(() -> new ResourceNotFoundException("Call not found with ID: " + callId));
        if (!call.getCallerId().equals(userId) && !call.getReceiverId().equals(userId) && !"ADMIN".equals(role)) {
            throw new SecurityException("Unauthorized access to call details");
        }
        return mapToDTO(call);
    }

    @Override
    @Transactional
    public void handleUserDisconnect(Long userId, String role) {
        List<CallStatus> active = List.of(CallStatus.INITIATED, CallStatus.RINGING, CallStatus.ACCEPTED);
        List<ConsultationCall> calls = consultationCallRepository.findActiveCallsForUser(userId, active);
        for (ConsultationCall call : calls) {
            LocalDateTime now = LocalDateTime.now();
            call.setEndedAt(now);
            if (call.getStatus() == CallStatus.ACCEPTED && call.getAnsweredAt() != null) {
                long seconds = Duration.between(call.getAnsweredAt(), now).getSeconds();
                call.setDurationSeconds((int) Math.max(0, seconds));
                call.setStatus(CallStatus.COMPLETED);
                call.setEndReason("DISCONNECTED");
            } else {
                call.setStatus(CallStatus.MISSED);
                call.setDurationSeconds(0);
                call.setEndReason("DISCONNECTED");
            }
            consultationCallRepository.save(call);
            log.info("Closed call {} due to socket disconnect for user {}", call.getId(), userId);
        }
    }

    private ConsultationCallDTO mapToDTO(ConsultationCall call) {
        if (call == null) return null;
        return ConsultationCallDTO.builder()
                .id(call.getId())
                .consultationRequestId(call.getConsultationRequestId())
                .callerId(call.getCallerId())
                .callerRole(call.getCallerRole())
                .callerName(call.getCallerName())
                .receiverId(call.getReceiverId())
                .receiverRole(call.getReceiverRole())
                .receiverName(call.getReceiverName())
                .callType(call.getCallType())
                .status(call.getStatus())
                .startedAt(call.getStartedAt())
                .answeredAt(call.getAnsweredAt())
                .endedAt(call.getEndedAt())
                .durationSeconds(call.getDurationSeconds())
                .endReason(call.getEndReason())
                .createdAt(call.getCreatedAt())
                .build();
    }
}
