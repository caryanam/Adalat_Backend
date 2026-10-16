package com.adalat.configuration;

import com.adalat.dto.ConsultationChatMessageDTO;
import com.adalat.dto.ConsultationSocketJoinRequest;
import com.adalat.dto.ConsultationSocketMessageRequest;
import com.adalat.enums.SenderType;
import com.adalat.security.JwtService;
import com.adalat.service.ConsultationChatService;
import com.adalat.service.ConsultationCallService;
import com.adalat.dto.ConsultationCallDTO;
import com.adalat.enums.CallType;
import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.AuthorizationResult;
import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.protocol.JacksonJsonSupport;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.adalat.event.ChatMessageCreatedEvent;
import com.adalat.event.ChatMessageDeliveredEvent;
import com.adalat.event.ChatMessageSeenEvent;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class SocketIOConfig {

    @Value("${socketio.host:0.0.0.0}")
    private String host;

    @Value("${socketio.port:9092}")
    private Integer port;

    private final JwtService jwtService;
    private final ConsultationChatService consultationChatService;
    private final ConsultationCallService consultationCallService;

    private SocketIOServer server;

    @Bean
    public SocketIOServer socketIOServer() {
        Configuration config = new Configuration();
        config.setHostname(host);
        config.setPort(port);
        config.setOrigin("*");
        config.setJsonSupport(new JacksonJsonSupport(new JavaTimeModule()));

        // Handshake Auth Listener - Permissive to prevent handshake drop
        config.setAuthorizationListener(data -> {
            return new AuthorizationResult(true);
        });

        this.server = new SocketIOServer(config);
        registerListeners(this.server);
        try {
            this.server.start();
            log.info("=== Socket.IO Server successfully started on {}:{} ===", host, port);
        } catch (Exception e) {
            log.error("Failed to start Socket.IO server: {}", e.getMessage(), e);
        }
        return this.server;
    }

    @EventListener
    public void onChatMessageCreated(ChatMessageCreatedEvent event) {
        if (server != null && event.getMessage() != null) {
            String roomName = "consultation:" + event.getRequestId();
            server.getRoomOperations(roomName).sendEvent("new_message", event.getMessage());
            log.info("Broadcasted new_message to {}: messageId={}", roomName, event.getMessage().getId());
        }
    }

    @EventListener
    public void onChatMessageDelivered(ChatMessageDeliveredEvent event) {
        if (server != null && event.getMessageIds() != null && !event.getMessageIds().isEmpty()) {
            String roomName = "consultation:" + event.getRequestId();
            Map<String, Object> payload = new HashMap<>();
            payload.put("consultationRequestId", event.getRequestId());
            payload.put("messageIds", event.getMessageIds());
            payload.put("status", "DELIVERED");

            server.getRoomOperations(roomName).sendEvent("message_status_updated", payload);
            server.getRoomOperations(roomName).sendEvent("messages_delivered", payload);
            log.info("Broadcasted messages_delivered to {}: ids={}", roomName, event.getMessageIds());
        }
    }

    @EventListener
    public void onChatMessageSeen(ChatMessageSeenEvent event) {
        if (server != null && event.getMessageIds() != null && !event.getMessageIds().isEmpty()) {
            String roomName = "consultation:" + event.getRequestId();
            Map<String, Object> payload = new HashMap<>();
            payload.put("consultationRequestId", event.getRequestId());
            payload.put("messageIds", event.getMessageIds());
            payload.put("status", "SEEN");

            server.getRoomOperations(roomName).sendEvent("message_status_updated", payload);
            server.getRoomOperations(roomName).sendEvent("messages_seen", payload);
            log.info("Broadcasted messages_seen to {}: ids={}", roomName, event.getMessageIds());
        }
    }

    @EventListener
    public void onNotificationCreated(com.adalat.event.NotificationCreatedEvent event) {
        if (server != null && event.getNotification() != null) {
            com.adalat.dto.NotificationDTO notif = event.getNotification();
            String userRoom = notif.getRecipientId() != null 
                    ? "user:" + notif.getRecipientRole() + ":" + notif.getRecipientId() 
                    : null;
            String roleRoom = "role:" + notif.getRecipientRole();

            if (userRoom != null) {
                server.getRoomOperations(userRoom).sendEvent("notification_received", notif);
                log.info("Broadcasted notification_received to {}: id={}, title={}", userRoom, notif.getId(), notif.getTitle());
            }

            server.getRoomOperations(roleRoom).sendEvent("notification_received", notif);
            log.info("Broadcasted notification_received to {}: id={}, title={}", roleRoom, notif.getId(), notif.getTitle());
        }
    }

    private Map<String, Object> toCallPayload(ConsultationCallDTO callDTO) {
        if (callDTO == null) return Map.of();
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", callDTO.getId());
        payload.put("consultationRequestId", callDTO.getConsultationRequestId());
        payload.put("callerId", callDTO.getCallerId());
        payload.put("callerRole", callDTO.getCallerRole());
        payload.put("callerName", callDTO.getCallerName());
        payload.put("receiverId", callDTO.getReceiverId());
        payload.put("receiverRole", callDTO.getReceiverRole());
        payload.put("receiverName", callDTO.getReceiverName());
        payload.put("callType", callDTO.getCallType() != null ? callDTO.getCallType().name() : "AUDIO");
        payload.put("status", callDTO.getStatus() != null ? callDTO.getStatus().name() : "RINGING");
        payload.put("durationSeconds", callDTO.getDurationSeconds() != null ? callDTO.getDurationSeconds() : 0);
        payload.put("endReason", callDTO.getEndReason());
        payload.put("startedAt", callDTO.getStartedAt() != null ? callDTO.getStartedAt().toString() : null);
        payload.put("answeredAt", callDTO.getAnsweredAt() != null ? callDTO.getAnsweredAt().toString() : null);
        payload.put("endedAt", callDTO.getEndedAt() != null ? callDTO.getEndedAt().toString() : null);
        payload.put("createdAt", callDTO.getCreatedAt() != null ? callDTO.getCreatedAt().toString() : null);
        return payload;
    }

    private void registerListeners(SocketIOServer server) {

        // Connect Listener
        server.addConnectListener(client -> {
            String token = client.getHandshakeData().getSingleUrlParam("token");
            if (token == null || token.isBlank()) {
                String authHeader = client.getHandshakeData().getHttpHeaders().get("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                }
            }

            if (token != null && !token.isBlank()) {
                try {
                    Jws<Claims> jws = jwtService.parse(token);
                    Claims claims = jws.getPayload();
                    Long userId = Long.valueOf(claims.getSubject());
                    String role = claims.get("role", String.class);
                    client.set("userId", userId);
                    client.set("role", role);

                    // Auto-join user-specific and role-specific notification rooms
                    String normRole = role != null ? role.toUpperCase().replace("ROLE_", "") : null;
                    client.set("userId", userId);
                    client.set("role", normRole);
                    if (normRole != null) {
                        client.joinRoom("user:" + normRole + ":" + userId);
                        client.joinRoom("user:" + role.toUpperCase() + ":" + userId);
                        client.joinRoom("user:" + userId);
                        client.joinRoom("role:" + normRole);
                        log.info("Socket client joined rooms: user:{}:{}, user:{}, role:{}", normRole, userId, userId, normRole);
                    }
                } catch (Exception e) {
                    log.warn("Failed to extract claims on connect: {}", e.getMessage());
                }
            }
            log.info("Socket.IO client connected: sessionId={}", client.getSessionId());
        });

        // Disconnect Listener
        server.addDisconnectListener(client -> {
            log.info("Socket.IO client disconnected: sessionId={}, userId={}",
                    client.getSessionId(), client.get("userId"));
        });

        // Socket Authenticate Event (allows explicit token authentication)
        server.addEventListener("authenticate", Map.class, (client, data, ackSender) -> {
            String token = data != null && data.get("token") != null ? data.get("token").toString() : null;
            if (token != null && !token.isBlank()) {
                try {
                    Jws<Claims> jws = jwtService.parse(token);
                    Claims claims = jws.getPayload();
                    Long userId = Long.valueOf(claims.getSubject());
                    String role = claims.get("role", String.class);
                    String normRole = role != null ? role.toUpperCase().replace("ROLE_", "") : null;
                    client.set("userId", userId);
                    client.set("role", normRole);
                    if (normRole != null) {
                        client.joinRoom("user:" + normRole + ":" + userId);
                        client.joinRoom("user:" + role.toUpperCase() + ":" + userId);
                        client.joinRoom("user:" + userId);
                        client.joinRoom("role:" + normRole);
                    }
                    log.info("Socket client successfully authenticated via event: userId={}, role={}", userId, normRole);
                    client.sendEvent("authenticated", Map.of("status", "SUCCESS", "userId", userId, "role", normRole != null ? normRole : ""));
                } catch (Exception e) {
                    log.warn("Socket authenticate event error: {}", e.getMessage());
                }
            }
        });

        // Direct message broadcast handler
        server.addEventListener("send_message_direct", ConsultationChatMessageDTO.class, (client, data, ackSender) -> {
            if (data != null && data.getConsultationRequestId() != null) {
                String roomName = "consultation:" + data.getConsultationRequestId();
                server.getRoomOperations(roomName).sendEvent("new_message", data);
                log.info("Direct broadcasted new_message to {}: messageId={}", roomName, data.getId());
            }
        });

        // Join Consultation Room Event
        server.addEventListener("join_consultation", ConsultationSocketJoinRequest.class, (client, data, ackSender) -> {
            Long userId = client.get("userId");
            String role = client.get("role");
            Long requestId = data != null ? data.getConsultationRequestId() : null;

            if (userId == null || requestId == null) {
                client.sendEvent("error", Map.of("message", "Invalid join request. consultationRequestId is required."));
                return;
            }

            String roomName = "consultation:" + requestId;
            client.joinRoom(roomName);

            SenderType senderType = "LAWYER".equalsIgnoreCase(role) ? SenderType.LAWYER : SenderType.CUSTOMER;
            // Mark pending messages as DELIVERED when recipient joins room
            List<Long> deliveredIds = consultationChatService.markMessagesDelivered(requestId, userId, senderType);

            Map<String, Object> joinedPayload = new HashMap<>();
            joinedPayload.put("consultationRequestId", requestId);
            joinedPayload.put("room", roomName);
            joinedPayload.put("status", "SUCCESS");
            client.sendEvent("joined_consultation", joinedPayload);

            log.info("User {} ({}) joined room: {}, delivered {} messages", userId, role, roomName, deliveredIds.size());
        });

        // Send Consultation Message Event
        server.addEventListener("send_message", ConsultationSocketMessageRequest.class, (client, data, ackSender) -> {
            Long userId = client.get("userId");
            String role = client.get("role");

            if (userId == null || data == null || data.getConsultationRequestId() == null || data.getMessage() == null || data.getMessage().isBlank()) {
                client.sendEvent("error", Map.of("message", "Invalid message payload. consultationRequestId and message are required."));
                return;
            }

            SenderType senderType = "LAWYER".equalsIgnoreCase(role) ? SenderType.LAWYER : SenderType.CUSTOMER;

            try {
                ConsultationChatMessageDTO savedMsg = consultationChatService.saveMessage(
                        data.getConsultationRequestId(),
                        userId,
                        senderType,
                        data.getMessage()
                );

                if (ackSender != null && ackSender.isAckRequested()) {
                    ackSender.sendAckData(savedMsg);
                }

                log.info("Message saved and broadcast via event: messageId={}, requestId={}", savedMsg.getId(), data.getConsultationRequestId());
            } catch (IllegalStateException e) {
                if ("FREE_CHAT_OVER".equals(e.getMessage())) {
                    client.sendEvent("error", Map.of("code", "FREE_CHAT_OVER", "message", "Free chat limit (2 mins) exceeded. Payment required."));
                } else {
                    client.sendEvent("error", Map.of("message", "Error saving message: " + e.getMessage()));
                }
            } catch (Exception e) {
                log.error("Failed to save message", e);
                client.sendEvent("error", Map.of("message", "Internal server error while saving message."));
            }
        });

        // Message Delivered Event (Receiver socket acknowledges receiving the message)
        server.addEventListener("message_delivered", ConsultationSocketJoinRequest.class, (client, data, ackSender) -> {
            Long userId = client.get("userId");
            String role = client.get("role");

            if (userId != null && data != null && data.getConsultationRequestId() != null) {
                SenderType recipientType = "LAWYER".equalsIgnoreCase(role) ? SenderType.LAWYER : SenderType.CUSTOMER;
                consultationChatService.markMessagesDelivered(data.getConsultationRequestId(), userId, recipientType);
            }
        });

        // Message Read / Seen Event (Receiver opens/views the consultation room)
        server.addEventListener("message_read", ConsultationSocketJoinRequest.class, (client, data, ackSender) -> {
            Long userId = client.get("userId");
            String role = client.get("role");

            if (userId != null && data != null && data.getConsultationRequestId() != null) {
                SenderType recipientType = "LAWYER".equalsIgnoreCase(role) ? SenderType.LAWYER : SenderType.CUSTOMER;
                consultationChatService.markMessagesSeen(data.getConsultationRequestId(), userId, recipientType);
            }
        });

        // Message Seen Event (Alias for message_read)
        server.addEventListener("message_seen", ConsultationSocketJoinRequest.class, (client, data, ackSender) -> {
            Long userId = client.get("userId");
            String role = client.get("role");

            if (userId != null && data != null && data.getConsultationRequestId() != null) {
                SenderType recipientType = "LAWYER".equalsIgnoreCase(role) ? SenderType.LAWYER : SenderType.CUSTOMER;
                consultationChatService.markMessagesSeen(data.getConsultationRequestId(), userId, recipientType);
            }
        });

        // Typing Start Event
        server.addEventListener("typing_start", ConsultationSocketJoinRequest.class, (client, data, ackSender) -> {
            Long userId = client.get("userId");
            String role = client.get("role");

            if (userId != null && data != null && data.getConsultationRequestId() != null) {
                String roomName = "consultation:" + data.getConsultationRequestId();
                Map<String, Object> payload = new HashMap<>();
                payload.put("consultationRequestId", data.getConsultationRequestId());
                payload.put("userId", userId);
                payload.put("role", role);
                payload.put("isTyping", true);
                server.getRoomOperations(roomName).sendEvent("user_typing", payload);
            }
        });

        // Typing Stop Event
        server.addEventListener("typing_stop", ConsultationSocketJoinRequest.class, (client, data, ackSender) -> {
            Long userId = client.get("userId");
            String role = client.get("role");

            if (userId != null && data != null && data.getConsultationRequestId() != null) {
                String roomName = "consultation:" + data.getConsultationRequestId();
                Map<String, Object> payload = new HashMap<>();
                payload.put("consultationRequestId", data.getConsultationRequestId());
                payload.put("userId", userId);
                payload.put("role", role);
                payload.put("isTyping", false);
                server.getRoomOperations(roomName).sendEvent("user_typing", payload);
            }
        });

        // Leave Consultation Event
        server.addEventListener("leave_consultation", ConsultationSocketJoinRequest.class, (client, data, ackSender) -> {
            if (data != null && data.getConsultationRequestId() != null) {
                String roomName = "consultation:" + data.getConsultationRequestId();
                client.leaveRoom(roomName);
                log.info("Client {} left room {}", client.getSessionId(), roomName);
            }
        });
    
        // ==================== WEBRTC AUDIO/VIDEO CALLING EVENTS ====================

        // 1. Call Initiate Event
        server.addEventListener("call_initiate", Map.class, (client, data, ackSender) -> {
            Long userId = client.get("userId");
            String role = client.get("role");

            if (userId == null || role == null || data == null || data.get("consultationRequestId") == null) {
                client.sendEvent("call_error", Map.of("message", "Invalid call request. Authentication and consultationRequestId are required."));
                return;
            }

            try {
                Long requestId = Long.valueOf(data.get("consultationRequestId").toString());
                String callTypeStr = data.get("callType") != null ? data.get("callType").toString() : "AUDIO";
                CallType callType = "VIDEO".equalsIgnoreCase(callTypeStr) ? CallType.VIDEO : CallType.AUDIO;

                ConsultationCallDTO callDTO = consultationCallService.initiateCall(requestId, userId, role, callType);

                // Notify recipient via private user room and consultation room
                String receiverRole = callDTO.getReceiverRole() != null ? callDTO.getReceiverRole().toUpperCase().replace("ROLE_", "") : "USER";
                String receiverRoom = "user:" + receiverRole + ":" + callDTO.getReceiverId();
                String fallbackUserRoom = "user:" + callDTO.getReceiverId();
                String consultationRoom = "consultation:" + requestId;

                Map<String, Object> callPayload = toCallPayload(callDTO);
                server.getRoomOperations(receiverRoom).sendEvent("incoming_call", callPayload);
                server.getRoomOperations(fallbackUserRoom).sendEvent("incoming_call", callPayload);
                server.getRoomOperations(consultationRoom).sendEvent("incoming_call", callPayload);

                // Send confirmation to caller
                client.sendEvent("call_initiated", callPayload);
                if (ackSender != null && ackSender.isAckRequested()) {
                    ackSender.sendAckData(callPayload);
                }

                log.info("Call initiated successfully: callId={}, from {}:{} to {}:{}", 
                        callDTO.getId(), role, userId, callDTO.getReceiverRole(), callDTO.getReceiverId());
            } catch (IllegalStateException e) {
                if ("RECEIVER_BUSY".equals(e.getMessage())) {
                    client.sendEvent("call_busy", Map.of("message", "The recipient is currently on another call."));
                } else if ("CALLER_ALREADY_IN_CALL".equals(e.getMessage())) {
                    client.sendEvent("call_error", Map.of("message", "You are already on an active call."));
                } else {
                    client.sendEvent("call_error", Map.of("message", e.getMessage()));
                }
            } catch (Exception e) {
                log.error("Failed to initiate call", e);
                client.sendEvent("call_error", Map.of("message", e.getMessage() != null ? e.getMessage() : "Internal server error initiating call."));
            }
        });

        // 2. Call Response Event (ACCEPT, REJECT, BUSY, TIMEOUT)
        server.addEventListener("call_response", Map.class, (client, data, ackSender) -> {
            Long userId = client.get("userId");
            String role = client.get("role");

            if (userId == null || role == null || data == null || data.get("callId") == null || data.get("response") == null) {
                client.sendEvent("call_error", Map.of("message", "Invalid call response payload."));
                return;
            }

            try {
                Long callId = Long.valueOf(data.get("callId").toString());
                String response = data.get("response").toString();

                ConsultationCallDTO callDTO = consultationCallService.respondToCall(callId, userId, role, response);

                String callerRole = callDTO.getCallerRole() != null ? callDTO.getCallerRole().toUpperCase().replace("ROLE_", "") : "USER";
                String callerRoom = "user:" + callerRole + ":" + callDTO.getCallerId();
                String fallbackCallerRoom = "user:" + callDTO.getCallerId();
                String consultationRoom = "consultation:" + callDTO.getConsultationRequestId();

                Map<String, Object> callPayload = toCallPayload(callDTO);
                if ("ACCEPT".equalsIgnoreCase(response)) {
                    server.getRoomOperations(callerRoom).sendEvent("call_accepted", callPayload);
                    server.getRoomOperations(fallbackCallerRoom).sendEvent("call_accepted", callPayload);
                    server.getRoomOperations(consultationRoom).sendEvent("call_accepted", callPayload);
                    client.sendEvent("call_accepted", callPayload);
                } else if ("REJECT".equalsIgnoreCase(response)) {
                    server.getRoomOperations(callerRoom).sendEvent("call_rejected", callPayload);
                    server.getRoomOperations(fallbackCallerRoom).sendEvent("call_rejected", callPayload);
                    server.getRoomOperations(consultationRoom).sendEvent("call_rejected", callPayload);
                    client.sendEvent("call_rejected", callPayload);
                } else if ("BUSY".equalsIgnoreCase(response)) {
                    server.getRoomOperations(callerRoom).sendEvent("call_busy", callPayload);
                    server.getRoomOperations(fallbackCallerRoom).sendEvent("call_busy", callPayload);
                    server.getRoomOperations(consultationRoom).sendEvent("call_busy", callPayload);
                    client.sendEvent("call_busy", callPayload);
                } else if ("TIMEOUT".equalsIgnoreCase(response)) {
                    server.getRoomOperations(callerRoom).sendEvent("call_missed", callPayload);
                    server.getRoomOperations(fallbackCallerRoom).sendEvent("call_missed", callPayload);
                    server.getRoomOperations(consultationRoom).sendEvent("call_missed", callPayload);
                    client.sendEvent("call_missed", callPayload);
                }

                if (ackSender != null && ackSender.isAckRequested()) {
                    ackSender.sendAckData(callPayload);
                }

                log.info("Call response handled: callId={}, response={}", callId, response);
            } catch (Exception e) {
                log.error("Failed to process call response", e);
                client.sendEvent("call_error", Map.of("message", e.getMessage() != null ? e.getMessage() : "Error processing call response."));
            }
        });

        // 3. WebRTC Offer Relay
        server.addEventListener("webrtc_offer", Map.class, (client, data, ackSender) -> {
            if (data != null && data.get("consultationRequestId") != null) {
                Long senderId = client.get("userId");
                String senderRole = client.get("role");
                Map<String, Object> payload = new HashMap<>(data);
                if (senderId != null) payload.put("senderId", senderId);
                if (senderRole != null) payload.put("senderRole", senderRole);

                if (data.get("targetUserId") != null) {
                    if (data.get("targetRole") != null) {
                        String targetRole = data.get("targetRole").toString().toUpperCase().replace("ROLE_", "");
                        String targetRoom = "user:" + targetRole + ":" + data.get("targetUserId");
                        server.getRoomOperations(targetRoom).sendEvent("webrtc_offer", payload);
                    }
                    server.getRoomOperations("user:" + data.get("targetUserId")).sendEvent("webrtc_offer", payload);
                }

                String consultationRoom = "consultation:" + data.get("consultationRequestId");
                for (SocketIOClient roomClient : server.getRoomOperations(consultationRoom).getClients()) {
                    if (!roomClient.getSessionId().equals(client.getSessionId())) {
                        roomClient.sendEvent("webrtc_offer", payload);
                    }
                }
                log.info("Relayed webrtc_offer for call {} from {}:{} to target {}:{}", 
                        data.get("callId"), senderRole, senderId, data.get("targetRole"), data.get("targetUserId"));
            }
        });

        // 4. WebRTC Answer Relay
        server.addEventListener("webrtc_answer", Map.class, (client, data, ackSender) -> {
            if (data != null && data.get("consultationRequestId") != null) {
                Long senderId = client.get("userId");
                String senderRole = client.get("role");
                Map<String, Object> payload = new HashMap<>(data);
                if (senderId != null) payload.put("senderId", senderId);
                if (senderRole != null) payload.put("senderRole", senderRole);

                if (data.get("targetUserId") != null) {
                    if (data.get("targetRole") != null) {
                        String targetRole = data.get("targetRole").toString().toUpperCase().replace("ROLE_", "");
                        String targetRoom = "user:" + targetRole + ":" + data.get("targetUserId");
                        server.getRoomOperations(targetRoom).sendEvent("webrtc_answer", payload);
                    }
                    server.getRoomOperations("user:" + data.get("targetUserId")).sendEvent("webrtc_answer", payload);
                }

                String consultationRoom = "consultation:" + data.get("consultationRequestId");
                for (SocketIOClient roomClient : server.getRoomOperations(consultationRoom).getClients()) {
                    if (!roomClient.getSessionId().equals(client.getSessionId())) {
                        roomClient.sendEvent("webrtc_answer", payload);
                    }
                }
                log.info("Relayed webrtc_answer for call {} from {}:{} to target {}:{}", 
                        data.get("callId"), senderRole, senderId, data.get("targetRole"), data.get("targetUserId"));
            }
        });

        // 5. WebRTC ICE Candidate Relay
        server.addEventListener("webrtc_ice_candidate", Map.class, (client, data, ackSender) -> {
            if (data != null && data.get("consultationRequestId") != null) {
                Long senderId = client.get("userId");
                String senderRole = client.get("role");
                Map<String, Object> payload = new HashMap<>(data);
                if (senderId != null) payload.put("senderId", senderId);
                if (senderRole != null) payload.put("senderRole", senderRole);

                if (data.get("targetUserId") != null) {
                    if (data.get("targetRole") != null) {
                        String targetRole = data.get("targetRole").toString().toUpperCase().replace("ROLE_", "");
                        String targetRoom = "user:" + targetRole + ":" + data.get("targetUserId");
                        server.getRoomOperations(targetRoom).sendEvent("webrtc_ice_candidate", payload);
                    }
                    server.getRoomOperations("user:" + data.get("targetUserId")).sendEvent("webrtc_ice_candidate", payload);
                }

                String consultationRoom = "consultation:" + data.get("consultationRequestId");
                for (SocketIOClient roomClient : server.getRoomOperations(consultationRoom).getClients()) {
                    if (!roomClient.getSessionId().equals(client.getSessionId())) {
                        roomClient.sendEvent("webrtc_ice_candidate", payload);
                    }
                }
            }
        });

        // 6. Call End Event
        server.addEventListener("call_end", Map.class, (client, data, ackSender) -> {
            Long userId = client.get("userId");
            if (userId == null || data == null || data.get("callId") == null) {
                return;
            }

            try {
                Long callId = Long.valueOf(data.get("callId").toString());
                String reason = data.get("reason") != null ? data.get("reason").toString() : "NORMAL";

                ConsultationCallDTO callDTO = consultationCallService.endCall(callId, userId, reason);

                String consultationRoom = "consultation:" + callDTO.getConsultationRequestId();
                String callerRole = callDTO.getCallerRole() != null ? callDTO.getCallerRole().toUpperCase().replace("ROLE_", "") : "USER";
                String receiverRole = callDTO.getReceiverRole() != null ? callDTO.getReceiverRole().toUpperCase().replace("ROLE_", "") : "USER";
                String callerRoom = "user:" + callerRole + ":" + callDTO.getCallerId();
                String receiverRoom = "user:" + receiverRole + ":" + callDTO.getReceiverId();

                Map<String, Object> callPayload = toCallPayload(callDTO);
                server.getRoomOperations(consultationRoom).sendEvent("call_ended", callPayload);
                server.getRoomOperations(callerRoom).sendEvent("call_ended", callPayload);
                server.getRoomOperations("user:" + callDTO.getCallerId()).sendEvent("call_ended", callPayload);
                server.getRoomOperations(receiverRoom).sendEvent("call_ended", callPayload);
                server.getRoomOperations("user:" + callDTO.getReceiverId()).sendEvent("call_ended", callPayload);

                if (ackSender != null && ackSender.isAckRequested()) {
                    ackSender.sendAckData(callPayload);
                }
                log.info("Call ended: callId={}, duration={}s, reason={}", callId, callDTO.getDurationSeconds(), reason);
            } catch (Exception e) {
                log.error("Failed to end call", e);
            }
        });

}

    @PostConstruct
    public void startServer() {
        try {
            if (server != null) {
                server.start();
                log.info("=== Socket.IO Server successfully started on {}:{} ===", host, port);
            }
        } catch (Exception e) {
            log.error("Failed to start Socket.IO server: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void stopServer() {
        if (server != null) {
            try {
                server.stop();
                log.info("=== Socket.IO Server stopped ===");
            } catch (Exception e) {
                log.warn("Socket.IO Server shutdown exception: {}", e.getMessage());
            }
        }
    }
}
