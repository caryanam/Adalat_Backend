package com.adalat.configuration;

import com.adalat.dto.ConsultationChatMessageDTO;
import com.adalat.dto.ConsultationSocketJoinRequest;
import com.adalat.dto.ConsultationSocketMessageRequest;
import com.adalat.enums.SenderType;
import com.adalat.security.JwtService;
import com.adalat.service.ConsultationChatService;
import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.AuthorizationResult;
import com.corundumstudio.socketio.Configuration;
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

    private SocketIOServer server;

    @Bean
    public SocketIOServer socketIOServer() {
        Configuration config = new Configuration();
        config.setHostname(host);
        config.setPort(port);
        config.setOrigin("*");

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
                    if (role != null) {
                        String userRoom = "user:" + role.toUpperCase() + ":" + userId;
                        String roleRoom = "role:" + role.toUpperCase();
                        client.joinRoom(userRoom);
                        client.joinRoom(roleRoom);
                        log.info("Socket client joined rooms: {} and {}", userRoom, roleRoom);
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
