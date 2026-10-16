package com.adalat.repository;

import com.adalat.entity.ConsultationCall;
import com.adalat.enums.CallStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConsultationCallRepository extends JpaRepository<ConsultationCall, Long> {

    List<ConsultationCall> findByConsultationRequestIdOrderByCreatedAtDesc(Long consultationRequestId);

    @Query("SELECT c FROM ConsultationCall c WHERE (c.callerId = :userId OR c.receiverId = :userId) ORDER BY c.createdAt DESC")
    List<ConsultationCall> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    @Query("SELECT c FROM ConsultationCall c WHERE (c.callerId = :userId OR c.receiverId = :userId) AND c.status IN :statuses ORDER BY c.createdAt DESC")
    List<ConsultationCall> findActiveCallsForUser(@Param("userId") Long userId, @Param("statuses") List<CallStatus> statuses);

    @Query("SELECT c FROM ConsultationCall c WHERE c.consultationRequestId = :requestId AND c.status IN :statuses ORDER BY c.createdAt DESC")
    List<ConsultationCall> findActiveCallsForRequest(@Param("requestId") Long requestId, @Param("statuses") List<CallStatus> statuses);
}
