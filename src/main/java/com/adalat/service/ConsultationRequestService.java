package com.adalat.service;

import com.adalat.dto.ConsultationPaymentInitiateDTO;
import com.adalat.dto.ConsultationPaymentVerifyDTO;
import com.adalat.dto.ConsultationRequestResponseDTO;
import com.adalat.dto.LawyerConsultationActionRequestDTO;

import java.util.List;

public interface ConsultationRequestService {

    ConsultationRequestResponseDTO createRequest(Long customerId, Long sessionId, Long lawyerId);

    List<ConsultationRequestResponseDTO> getRequestsForLawyer(Long lawyerId);

    ConsultationRequestResponseDTO acceptRequest(Long lawyerId, Long requestId, LawyerConsultationActionRequestDTO actionDTO);

    ConsultationRequestResponseDTO rejectRequest(Long lawyerId, Long requestId, LawyerConsultationActionRequestDTO actionDTO);

    List<ConsultationRequestResponseDTO> getRequestsForCustomer(Long customerId);

    ConsultationRequestResponseDTO getConsultationForCustomer(Long customerId, Long requestId);

    ConsultationRequestResponseDTO getConsultationForLawyer(Long lawyerId, Long requestId);

    List<ConsultationRequestResponseDTO> getCustomerConsultations(Long customerId, String statusFilter);

    List<ConsultationRequestResponseDTO> getLawyerConsultations(Long lawyerId, String statusFilter);

    ConsultationPaymentInitiateDTO initiateConsultationPayment(Long customerId, Long requestId);

    ConsultationRequestResponseDTO verifyConsultationPayment(Long customerId, Long requestId, ConsultationPaymentVerifyDTO verifyDTO);

    ConsultationRequestResponseDTO completeConsultationByCustomer(Long customerId, Long requestId);

    ConsultationRequestResponseDTO completeConsultationByLawyer(Long lawyerId, Long requestId);

    ConsultationRequestResponseDTO confirmAppointmentByCustomer(Long customerId, Long requestId, String action);

    ConsultationRequestResponseDTO unlockPaidConsultation(Long requestId, String paymentId);
}
