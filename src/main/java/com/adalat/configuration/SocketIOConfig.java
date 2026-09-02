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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

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

        // Handshake Auth Listener
        config.setAuthorizationListener(data -> {
            String token = data.getSingleUrlParam("token");
            if (token == null || token.isBlank()) {
                String authHeader = data.getHttpHeaders().get("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                }
            }

            if (token == null || token.isBlank()) {
                log.warn("Socket.IO connection rejected: missing JWT token");
                return new AuthorizationResult(false);
            }

            try {
                Jws<Claims> jws = jwtService.parse(token);
                Claims claims = jws.getPayload();
                Long userId = Long.valueOf(claims.getSubject());
                String role = claims.get("role", String.class);
                boolean isValid = userId != null && role != null;
                return new AuthorizationResult(isValid);
            } catch (Exception e) {
                log.warn("Socket.IO connection rejected: invalid JWT token - {}", e.getMessage());
                return new AuthorizationResult(false);
            }
        });

        this.server = new SocketIOServer(config);
        registerListeners(this.server);
        return this.server;
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

            try {
                Jws<Claims> jws = jwtService.parse(token);
                Claims claims = jws.getPayload();
                Long userId = Long.valueOf(claims.getSubject());
                String role = claims.get("role", String.class);

                client.set("userId", userId);
                client.set("role", role);

                log.info("Socket.IO client connected: sessionId={}, userId={}, role={}",
                        client.getSessionId(), userId, role);
            } catch (Exception e) {
                log.warn("Failed to extract claims on connect: {}", e.getMessage());
                client.disconnect();
            }
        });

        // Disconnect Listener
        server.addDisconnectListener(client -> {
            log.info("Socket.IO client disconnected: sessionId={}, userId={}",
                    client.getSessionId(), client.get("userId"));
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
            consultationChatService.markMessagesDelivered(requestId, userId, senderType);

            client.sendEvent("joined_consultation", Map.of(
                    "consultationRequestId", requestId,
                    "room", roomName,
                    "status", "SUCCESS"
            ));

            log.info("User {} ({}) joined room: {}", userId, role, roomName);
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

                String roomName = "consultation:" + data.getConsultationRequestId();

                // Broadcast new message to all clients in the room (including sender)
                server.getRoomOperations(roomName).sendEvent("new_message", savedMsg);

                if (ackSender != null && ackSender.isAckRequested()) {
                    ackSender.sendAckData(savedMsg);
                }

                log.info("Message sent in room {}: messageId={}, senderId={}", roomName, savedMsg.getId(), userId);
            } catch (Exception e) {
                log.error("Failed to send consultation message: {}", e.getMessage());
                client.sendEvent("error", Map.of("message", e.getMessage()));
            }
        });

        // Message Seen Event
        server.addEventListener("message_seen", ConsultationSocketJoinRequest.class, (client, data, ackSender) -> {
            Long userId = client.get("userId");
            String role = client.get("role");

            if (userId != null && data != null && data.getConsultationRequestId() != null) {
                SenderType recipientType = "LAWYER".equalsIgnoreCase(role) ? SenderType.LAWYER : SenderType.CUSTOMER;
                consultationChatService.markMessagesSeen(data.getConsultationRequestId(), userId, recipientType);

                String roomName = "consultation:" + data.getConsultationRequestId();
                server.getRoomOperations(roomName).sendEvent("messages_seen", Map.of(
                        "consultationRequestId", data.getConsultationRequestId(),
                        "seenByUserId", userId,
                        "seenByRole", role
                ));
            }
        });

        // Typing Start Event
        server.addEventListener("typing_start", ConsultationSocketJoinRequest.class, (client, data, ackSender) -> {
            Long userId = client.get("userId");
            String role = client.get("role");

            if (userId != null && data != null && data.getConsultationRequestId() != null) {
                String roomName = "consultation:" + data.getConsultationRequestId();
                server.getRoomOperations(roomName).sendEvent("user_typing", Map.of(
                        "consultationRequestId", data.getConsultationRequestId(),
                        "userId", userId,
                        "role", role,
                        "isTyping", true
                ));
            }
        });

        // Typing Stop Event
        server.addEventListener("typing_stop", ConsultationSocketJoinRequest.class, (client, data, ackSender) -> {
            Long userId = client.get("userId");
            String role = client.get("role");

            if (userId != null && data != null && data.getConsultationRequestId() != null) {
                String roomName = "consultation:" + data.getConsultationRequestId();
                server.getRoomOperations(roomName).sendEvent("user_typing", Map.of(
                        "consultationRequestId", data.getConsultationRequestId(),
                        "userId", userId,
                        "role", role,
                        "isTyping", false
                ));
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
