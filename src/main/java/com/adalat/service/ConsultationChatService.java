package com.adalat.service;

import com.adalat.dto.ConsultationChatMessageDTO;
import com.adalat.enums.SenderType;

import java.util.List;

public interface ConsultationChatService {

    List<ConsultationChatMessageDTO> getMessagesForCustomer(Long customerId, Long requestId);

    List<ConsultationChatMessageDTO> getMessagesForLawyer(Long lawyerId, Long requestId);

    ConsultationChatMessageDTO saveMessage(Long requestId, Long senderId, SenderType senderType, String message);

    void markMessagesDelivered(Long requestId, Long recipientId, SenderType recipientType);

    void markMessagesSeen(Long requestId, Long recipientId, SenderType recipientType);
}
