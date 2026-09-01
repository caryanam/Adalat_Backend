package com.adalat.repository;

import com.adalat.entity.ConsultationChatMessage;
import com.adalat.entity.ConsultationRequest;
import com.adalat.enums.ChatMessageStatus;
import com.adalat.enums.SenderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConsultationChatMessageRepository extends JpaRepository<ConsultationChatMessage, Long> {

    List<ConsultationChatMessage> findByConsultationRequestOrderByCreatedAtAsc(ConsultationRequest request);

    List<ConsultationChatMessage> findByConsultationRequestAndSenderTypeNotAndStatus(
            ConsultationRequest request, SenderType senderType, ChatMessageStatus status);

    List<ConsultationChatMessage> findByConsultationRequestAndSenderTypeNotAndStatusNot(
            ConsultationRequest request, SenderType senderType, ChatMessageStatus status);

    long countByConsultationRequestAndSenderTypeNotAndStatusNot(
            ConsultationRequest request, SenderType senderType, ChatMessageStatus status);
}
