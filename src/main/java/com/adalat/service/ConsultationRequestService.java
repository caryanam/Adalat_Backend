package com.adalat.service;

import com.adalat.dto.ConsultationRequestResponseDTO;
import com.adalat.dto.LawyerConsultationActionRequestDTO;

import java.util.List;

public interface ConsultationRequestService {

    ConsultationRequestResponseDTO createRequest(Long customerId, Long sessionId, Long lawyerId);

    List<ConsultationRequestResponseDTO> getRequestsForLawyer(Long lawyerId);

    ConsultationRequestResponseDTO acceptRequest(Long lawyerId, Long requestId, LawyerConsultationActionRequestDTO actionDTO);

    ConsultationRequestResponseDTO rejectRequest(Long lawyerId, Long requestId, LawyerConsultationActionRequestDTO actionDTO);

    List<ConsultationRequestResponseDTO> getRequestsForCustomer(Long customerId);
}
