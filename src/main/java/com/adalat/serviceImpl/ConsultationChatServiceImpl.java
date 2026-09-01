package com.adalat.serviceImpl;

import com.adalat.dto.ConsultationChatMessageDTO;
import com.adalat.entity.ConsultationChatMessage;
import com.adalat.entity.ConsultationRequest;
import com.adalat.entity.Customer;
import com.adalat.entity.Lawyer;
import com.adalat.enums.ChatMessageStatus;
import com.adalat.enums.ConsultationRequestStatus;
import com.adalat.enums.SenderType;
import com.adalat.exception.ResourceNotFoundException;
import com.adalat.repository.ConsultationChatMessageRepository;
import com.adalat.repository.ConsultationRequestRepository;
import com.adalat.repository.CustomerRepository;
import com.adalat.repository.LawyerRepository;
import com.adalat.service.ConsultationChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsultationChatServiceImpl implements ConsultationChatService {

    private final ConsultationChatMessageRepository chatMessageRepository;
    private final ConsultationRequestRepository consultationRequestRepository;
    private final CustomerRepository customerRepository;
    private final LawyerRepository lawyerRepository;

    @Override
    @Transactional
    public List<ConsultationChatMessageDTO> getMessagesForCustomer(Long customerId, Long requestId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        ConsultationRequest request = consultationRequestRepository.findByIdAndCustomer(requestId, customer)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation request not found: " + requestId));

        // Mark unread incoming messages as SEEN
        markMessagesSeenInternal(request, SenderType.CUSTOMER);

        return chatMessageRepository.findByConsultationRequestOrderByCreatedAtAsc(request)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<ConsultationChatMessageDTO> getMessagesForLawyer(Long lawyerId, Long requestId) {
        Lawyer lawyer = lawyerRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer not found with ID: " + lawyerId));

        ConsultationRequest request = consultationRequestRepository.findByIdAndLawyer(requestId, lawyer)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation request not found: " + requestId));

        // Mark unread incoming messages as SEEN
        markMessagesSeenInternal(request, SenderType.LAWYER);

        return chatMessageRepository.findByConsultationRequestOrderByCreatedAtAsc(request)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ConsultationChatMessageDTO saveMessage(Long requestId, Long senderId, SenderType senderType, String message) {
        ConsultationRequest request = consultationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation request not found: " + requestId));

        if (request.getStatus() != ConsultationRequestStatus.ACTIVE) {
            throw new IllegalArgumentException("Cannot send messages. Consultation is not active (Status: " + request.getStatus() + ").");
        }

        // Verify sender belongs to consultation
        if (senderType == SenderType.CUSTOMER) {
            if (!request.getCustomer().getCustomerId().equals(senderId)) {
                throw new IllegalArgumentException("Sender customer ID does not match this consultation.");
            }
        } else if (senderType == SenderType.LAWYER) {
            if (!request.getLawyer().getLawyerId().equals(senderId)) {
                throw new IllegalArgumentException("Sender lawyer ID does not match this consultation.");
            }
        }

        ConsultationChatMessage chatMessage = ConsultationChatMessage.builder()
                .consultationRequest(request)
                .senderId(senderId)
                .senderType(senderType)
                .message(message)
                .status(ChatMessageStatus.SENT)
                .build();

        ConsultationChatMessage saved = chatMessageRepository.save(chatMessage);
        log.info("Consultation message saved: id={}, requestId={}, senderType={}", saved.getId(), requestId, senderType);

        return toDTO(saved);
    }

    @Override
    @Transactional
    public void markMessagesDelivered(Long requestId, Long recipientId, SenderType recipientType) {
        ConsultationRequest request = consultationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation request not found: " + requestId));

        // Incoming messages for recipient are those where senderType != recipientType
        List<ConsultationChatMessage> undelivered = chatMessageRepository.findByConsultationRequestAndSenderTypeNotAndStatus(
                request, recipientType, ChatMessageStatus.SENT);

        LocalDateTime now = LocalDateTime.now();
        for (ConsultationChatMessage msg : undelivered) {
            msg.setStatus(ChatMessageStatus.DELIVERED);
            msg.setDeliveredAt(now);
        }
        chatMessageRepository.saveAll(undelivered);
    }

    @Override
    @Transactional
    public void markMessagesSeen(Long requestId, Long recipientId, SenderType recipientType) {
        ConsultationRequest request = consultationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation request not found: " + requestId));

        markMessagesSeenInternal(request, recipientType);
    }

    private void markMessagesSeenInternal(ConsultationRequest request, SenderType recipientType) {
        List<ConsultationChatMessage> unseen = chatMessageRepository.findByConsultationRequestAndSenderTypeNotAndStatusNot(
                request, recipientType, ChatMessageStatus.SEEN);

        LocalDateTime now = LocalDateTime.now();
        for (ConsultationChatMessage msg : unseen) {
            if (msg.getDeliveredAt() == null) {
                msg.setDeliveredAt(now);
            }
            msg.setStatus(ChatMessageStatus.SEEN);
            msg.setSeenAt(now);
        }
        if (!unseen.isEmpty()) {
            chatMessageRepository.saveAll(unseen);
        }
    }

    private ConsultationChatMessageDTO toDTO(ConsultationChatMessage msg) {
        String senderName = "User";
        if (msg.getSenderType() == SenderType.CUSTOMER && msg.getConsultationRequest().getCustomer() != null) {
            senderName = msg.getConsultationRequest().getCustomer().getFullName();
        } else if (msg.getSenderType() == SenderType.LAWYER && msg.getConsultationRequest().getLawyer() != null) {
            senderName = msg.getConsultationRequest().getLawyer().getFullName();
        }

        return ConsultationChatMessageDTO.builder()
                .id(msg.getId())
                .consultationRequestId(msg.getConsultationRequest().getId())
                .senderId(msg.getSenderId())
                .senderType(msg.getSenderType())
                .senderName(senderName)
                .message(msg.getMessage())
                .status(msg.getStatus())
                .createdAt(msg.getCreatedAt())
                .deliveredAt(msg.getDeliveredAt())
                .seenAt(msg.getSeenAt())
                .build();
    }
}
