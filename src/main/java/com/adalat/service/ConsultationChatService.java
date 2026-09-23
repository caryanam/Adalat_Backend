package com.adalat.service;

import com.adalat.dto.ConsultationChatMessageDTO;
import com.adalat.enums.SenderType;

import java.util.List;

public interface ConsultationChatService {

    List<ConsultationChatMessageDTO> getMessagesForCustomer(Long customerId, Long requestId);

    List<ConsultationChatMessageDTO> getMessagesForLawyer(Long lawyerId, Long requestId);

    ConsultationChatMessageDTO saveMessage(Long requestId, Long senderId, SenderType senderType, String message);

    ConsultationChatMessageDTO saveMessage(Long requestId, Long senderId, SenderType senderType, String message, 
                                          String attachmentUrl, String attachmentName, String attachmentType, Long attachmentSize);

    ConsultationChatMessageDTO uploadAndSaveAttachment(Long requestId, Long senderId, SenderType senderType, 
                                                       org.springframework.web.multipart.MultipartFile file, String text);

    List<Long> markMessagesDelivered(Long requestId, Long recipientId, SenderType recipientType);

    List<Long> markMessagesDelivered(Long requestId, Long recipientId, SenderType recipientType, List<Long> messageIds);

    List<Long> markMessagesSeen(Long requestId, Long recipientId, SenderType recipientType);

    List<Long> markMessagesSeen(Long requestId, Long recipientId, SenderType recipientType, List<Long> messageIds);
}
