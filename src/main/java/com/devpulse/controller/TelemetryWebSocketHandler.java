package com.devpulse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.devpulse.model.TelemetryEvent;
import com.devpulse.model.TelemetrySession;
import com.devpulse.repository.TelemetryEventRepository;
import com.devpulse.repository.TelemetrySessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class TelemetryWebSocketHandler extends TextWebSocketHandler {

    private final TelemetrySessionRepository sessionRepository;
    private final TelemetryEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public TelemetryWebSocketHandler(TelemetrySessionRepository sessionRepository,
                                     TelemetryEventRepository eventRepository,
                                     ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) throws Exception {
        String payload = message.getPayload();
        
        try {
            // Parse incoming telemetry payload
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String sessionToken = (String) data.get("sessionToken");
            Integer keystrokeCount = ((Number) data.getOrDefault("keystrokeCount", 0)).intValue();
            Double typingSpeedWpm = ((Number) data.getOrDefault("typingSpeedWpm", 0.0)).doubleValue();
            String activeFileExtension = (String) data.getOrDefault("activeFileExtension", "java");
            Integer intervalSeconds = ((Number) data.getOrDefault("intervalSeconds", 5)).intValue();

            if (sessionToken == null || sessionToken.trim().isEmpty()) {
                return;
            }

            // Find or create session
            TelemetrySession session = sessionRepository.findBySessionToken(sessionToken)
                    .orElseGet(() -> {
                        TelemetrySession newSession = new TelemetrySession(sessionToken, LocalDateTime.now());
                        return sessionRepository.save(newSession);
                    });

            // Update session aggregates
            session.setTotalKeystrokes(session.getTotalKeystrokes() + keystrokeCount);
            if (keystrokeCount > 0) {
                session.setActiveTimeSeconds(session.getActiveTimeSeconds() + intervalSeconds);
            }
            sessionRepository.save(session);

            // Log event
            TelemetryEvent event = new TelemetryEvent(
                    sessionToken,
                    LocalDateTime.now(),
                    keystrokeCount,
                    typingSpeedWpm,
                    activeFileExtension,
                    intervalSeconds
            );
            eventRepository.save(event);

            // Calculate current focus level & fatigue alert
            // Fatigue heuristic: if session active time > 10 minutes (for testing, let's say 2 minutes) 
            // and average typing speed drops below 15 WPM during active work
            String focusLevel = "Focus Mode";
            boolean fatigueAlert = false;
            
            if (session.getActiveTimeSeconds() > 120 && typingSpeedWpm < 15.0 && keystrokeCount > 0) {
                focusLevel = "Fatigued (Need a break)";
                fatigueAlert = true;
            } else if (keystrokeCount == 0) {
                focusLevel = "Idle";
            } else if (typingSpeedWpm > 50.0) {
                focusLevel = "Flow State";
            }

            // Send updated stats back to browser
            Map<String, Object> response = new HashMap<>();
            response.put("type", "telemetry_update");
            response.put("totalKeystrokes", session.getTotalKeystrokes());
            response.put("activeTimeSeconds", session.getActiveTimeSeconds());
            response.put("typingSpeedWpm", typingSpeedWpm);
            response.put("focusLevel", focusLevel);
            response.put("fatigueAlert", fatigueAlert);

            if (wsSession.isOpen()) {
                wsSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            }

        } catch (Exception e) {
            System.err.println("Error processing telemetry: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
