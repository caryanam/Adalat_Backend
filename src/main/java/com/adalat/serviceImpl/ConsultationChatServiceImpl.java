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
import com.adalat.event.ChatMessageCreatedEvent;
import com.adalat.event.ChatMessageDeliveredEvent;
import com.adalat.event.ChatMessageSeenEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final com.adalat.repository.LawyerDocumentRepository lawyerDocumentRepository;
    private final com.adalat.repository.PaymentTransactionRepository paymentTransactionRepository;
    private final com.adalat.service.FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public List<ConsultationChatMessageDTO> getMessagesForCustomer(Long customerId, Long requestId) {
        ConsultationRequest request = consultationRequestRepository.findById(requestId)
                .orElse(null);

        if (request == null) return List.of();

        // Strictly fetch messages with current database status (text or attachment present) - DO NOT auto-mark as SEEN
        return chatMessageRepository.findByConsultationRequestOrderByCreatedAtAsc(request)
                .stream()
                .filter(m -> (m.getMessage() != null && !m.getMessage().trim().isEmpty()) || 
                             (m.getAttachmentUrl() != null && !m.getAttachmentUrl().trim().isEmpty()))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsultationChatMessageDTO> getMessagesForLawyer(Long lawyerId, Long requestId) {
        ConsultationRequest request = consultationRequestRepository.findById(requestId)
                .orElse(null);

        if (request == null) return List.of();

        // Strictly fetch messages with current database status (text or attachment present) - DO NOT auto-mark as SEEN
        return chatMessageRepository.findByConsultationRequestOrderByCreatedAtAsc(request)
                .stream()
                .filter(m -> (m.getMessage() != null && !m.getMessage().trim().isEmpty()) || 
                             (m.getAttachmentUrl() != null && !m.getAttachmentUrl().trim().isEmpty()))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ConsultationChatMessageDTO saveMessage(Long requestId, Long senderId, SenderType senderType, String message) {
        return saveMessage(requestId, senderId, senderType, message, null, null, null, null);
    }

    @Override
    @Transactional
    public ConsultationChatMessageDTO saveMessage(Long requestId, Long senderId, SenderType senderType, String message,
                                                  String attachmentUrl, String attachmentName, String attachmentType, Long attachmentSize) {
        boolean hasText = message != null && !message.trim().isEmpty();
        boolean hasAttachment = attachmentUrl != null && !attachmentUrl.trim().isEmpty();

        if (!hasText && !hasAttachment) {
            throw new IllegalArgumentException("Message must contain either text or an attachment.");
        }

        ConsultationRequest request = consultationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation request not found: " + requestId));

        if (request.getStatus() != ConsultationRequestStatus.ACTIVE && request.getStatus() != ConsultationRequestStatus.COMPLETED) {
            request.setStatus(ConsultationRequestStatus.ACTIVE);
            consultationRequestRepository.save(request);
        }

        if (request.getChatStartedAt() == null) {
            request.setChatStartedAt(LocalDateTime.now());
            consultationRequestRepository.save(request);
        }

        if (senderType == SenderType.CUSTOMER) {
            boolean isFreeChatTimeOver = LocalDateTime.now().isAfter(request.getChatStartedAt().plusMinutes(2));
            if (isFreeChatTimeOver) {
                boolean isPaid = paymentTransactionRepository.findByConsultationRequestAndStatus(request, com.adalat.enums.PaymentStatus.PAID).isPresent();
                if (!isPaid) {
                    throw new IllegalStateException("FREE_CHAT_OVER");
                }
            }
        }

        Long actualSenderId = senderId;
        if (senderType == SenderType.CUSTOMER && request.getCustomer() != null) {
            actualSenderId = request.getCustomer().getCustomerId();
        } else if (senderType == SenderType.LAWYER && request.getLawyer() != null) {
            actualSenderId = request.getLawyer().getLawyerId();
        }

        ConsultationChatMessage chatMessage = ConsultationChatMessage.builder()
                .consultationRequest(request)
                .senderId(actualSenderId)
                .senderType(senderType)
                .message(hasText ? message.trim() : "")
                .attachmentUrl(attachmentUrl)
                .attachmentName(attachmentName)
                .attachmentType(attachmentType)
                .attachmentSize(attachmentSize)
                .status(ChatMessageStatus.SENT) // Saved strictly as SENT (1 grey tick)
                .build();

        ConsultationChatMessage saved = chatMessageRepository.save(chatMessage);
        log.info("Consultation message saved: id={}, requestId={}, senderType={}, hasAttachment={}, status=SENT", 
                saved.getId(), requestId, senderType, hasAttachment);

        ConsultationChatMessageDTO dto = toDTO(saved);
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new ChatMessageCreatedEvent(this, requestId, dto));
        }

        return dto;
    }

    @Override
    @Transactional
    public ConsultationChatMessageDTO uploadAndSaveAttachment(Long requestId, Long senderId, SenderType senderType, 
                                                              org.springframework.web.multipart.MultipartFile file, String text) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Attachment file cannot be empty.");
        }

        try {
            String attachmentUrl = fileStorageService.storeConsultationAttachment(requestId, file);
            String attachmentName = file.getOriginalFilename();
            String attachmentType = file.getContentType();
            Long attachmentSize = file.getSize();

            return saveMessage(requestId, senderId, senderType, text, attachmentUrl, attachmentName, attachmentType, attachmentSize);
        } catch (java.io.IOException e) {
            log.error("Failed to store consultation attachment for requestId={}", requestId, e);
            throw new RuntimeException("Failed to upload attachment: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public List<Long> markMessagesDelivered(Long requestId, Long recipientId, SenderType recipientType) {
        return markMessagesDelivered(requestId, recipientId, recipientType, null);
    }

    @Override
    @Transactional
    public List<Long> markMessagesDelivered(Long requestId, Long recipientId, SenderType recipientType, List<Long> messageIds) {
        ConsultationRequest request = consultationRequestRepository.findById(requestId)
                .orElse(null);

        if (request == null) return List.of();

        List<ConsultationChatMessage> undelivered = chatMessageRepository.findByConsultationRequestAndSenderTypeNotAndStatus(
                request, recipientType, ChatMessageStatus.SENT);

        if (messageIds != null && !messageIds.isEmpty()) {
            undelivered = undelivered.stream()
                    .filter(m -> messageIds.contains(m.getId()))
                    .collect(Collectors.toList());
        }

        if (undelivered.isEmpty()) return List.of();

        LocalDateTime now = LocalDateTime.now();
        List<Long> updatedIds = new ArrayList<>();
        for (ConsultationChatMessage msg : undelivered) {
            msg.setStatus(ChatMessageStatus.DELIVERED);
            msg.setDeliveredAt(now);
            updatedIds.add(msg.getId());
        }
        chatMessageRepository.saveAll(undelivered);
        log.info("Marked messages as DELIVERED: requestId={}, recipientType={}, messageIds={}", requestId, recipientType, updatedIds);

        if (eventPublisher != null && !updatedIds.isEmpty()) {
            eventPublisher.publishEvent(new ChatMessageDeliveredEvent(this, requestId, updatedIds));
        }

        return updatedIds;
    }

    @Override
    @Transactional
    public List<Long> markMessagesSeen(Long requestId, Long recipientId, SenderType recipientType) {
        return markMessagesSeen(requestId, recipientId, recipientType, null);
    }

    @Override
    @Transactional
    public List<Long> markMessagesSeen(Long requestId, Long recipientId, SenderType recipientType, List<Long> messageIds) {
        ConsultationRequest request = consultationRequestRepository.findById(requestId)
                .orElse(null);

        if (request == null) return List.of();

        List<ConsultationChatMessage> unseen = chatMessageRepository.findByConsultationRequestAndSenderTypeNotAndStatusNot(
                request, recipientType, ChatMessageStatus.SEEN);

        if (messageIds != null && !messageIds.isEmpty()) {
            unseen = unseen.stream()
                    .filter(m -> messageIds.contains(m.getId()))
                    .collect(Collectors.toList());
        }

        if (unseen.isEmpty()) return List.of();

        LocalDateTime now = LocalDateTime.now();
        List<Long> updatedIds = new ArrayList<>();
        for (ConsultationChatMessage msg : unseen) {
            if (msg.getDeliveredAt() == null) {
                msg.setDeliveredAt(now);
            }
            msg.setStatus(ChatMessageStatus.SEEN);
            msg.setSeenAt(now);
            updatedIds.add(msg.getId());
        }
        chatMessageRepository.saveAll(unseen);
        log.info("Marked messages as SEEN/READ: requestId={}, recipientType={}, messageIds={}", requestId, recipientType, updatedIds);

        if (eventPublisher != null && !updatedIds.isEmpty()) {
            eventPublisher.publishEvent(new ChatMessageSeenEvent(this, requestId, updatedIds));
        }

        return updatedIds;
    }

    private ConsultationChatMessageDTO toDTO(ConsultationChatMessage msg) {
        String senderName = "User";
        String senderProfileImageUrl = null;
        if (msg.getSenderType() == SenderType.CUSTOMER && msg.getConsultationRequest().getCustomer() != null) {
            senderName = msg.getConsultationRequest().getCustomer().getFullName();
        } else if (msg.getSenderType() == SenderType.LAWYER && msg.getConsultationRequest().getLawyer() != null) {
            senderName = msg.getConsultationRequest().getLawyer().getFullName();
            senderProfileImageUrl = msg.getConsultationRequest().getLawyer().getProfilePhotoUrl();
            if ((senderProfileImageUrl == null || senderProfileImageUrl.isBlank()) && lawyerDocumentRepository != null) {
                List<com.adalat.entity.LawyerDocument> docs = lawyerDocumentRepository.findByLawyer(msg.getConsultationRequest().getLawyer());
                if (docs != null && !docs.isEmpty()) {
                    senderProfileImageUrl = docs.stream()
                            .filter(d -> d.getDocumentType() == com.adalat.enums.DocumentType.PHOTO ||
                                         (d.getFileUrl() != null && d.getFileUrl().toLowerCase().matches(".*\\.(jpg|jpeg|png|webp|gif)$")))
                            .map(com.adalat.entity.LawyerDocument::getFileUrl)
                            .findFirst()
                            .orElse(null);
                }
            }
        }

        return ConsultationChatMessageDTO.builder()
                .id(msg.getId())
                .consultationRequestId(msg.getConsultationRequest().getId())
                .senderId(msg.getSenderId())
                .senderType(msg.getSenderType())
                .senderName(senderName)
                .senderProfileImageUrl(senderProfileImageUrl)
                .message(msg.getMessage())
                .attachmentUrl(msg.getAttachmentUrl())
                .attachmentName(msg.getAttachmentName())
                .attachmentType(msg.getAttachmentType())
                .attachmentSize(msg.getAttachmentSize())
                .status(msg.getStatus())
                .createdAt(msg.getCreatedAt())
                .deliveredAt(msg.getDeliveredAt())
                .seenAt(msg.getSeenAt())
                .build();
    }
}
