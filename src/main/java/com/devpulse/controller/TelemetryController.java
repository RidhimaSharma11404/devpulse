package com.devpulse.controller;

import com.devpulse.model.CompileEvent;
import com.devpulse.model.TelemetryEvent;
import com.devpulse.model.TelemetrySession;
import com.devpulse.repository.CompileEventRepository;
import com.devpulse.repository.TelemetryEventRepository;
import com.devpulse.repository.TelemetrySessionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/telemetry")
@CrossOrigin(origins = "*")
public class TelemetryController {

    private final TelemetrySessionRepository sessionRepository;
    private final TelemetryEventRepository eventRepository;
    private final CompileEventRepository compileEventRepository;

    public TelemetryController(TelemetrySessionRepository sessionRepository,
                               TelemetryEventRepository eventRepository,
                               CompileEventRepository compileEventRepository) {
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.compileEventRepository = compileEventRepository;
    }

    @GetMapping("/session/{token}")
    public ResponseEntity<?> getSession(@PathVariable String token) {
        return sessionRepository.findBySessionToken(token)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/session/{token}/start")
    public ResponseEntity<?> startSession(@PathVariable String token) {
        TelemetrySession session = sessionRepository.findBySessionToken(token)
                .orElseGet(() -> {
                    TelemetrySession newSession = new TelemetrySession(token, LocalDateTime.now());
                    return sessionRepository.save(newSession);
                });
        return ResponseEntity.ok(session);
    }

    @PostMapping("/session/{token}/stop")
    public ResponseEntity<?> stopSession(@PathVariable String token) {
        return sessionRepository.findBySessionToken(token)
                .map(session -> {
                    session.setEndTime(LocalDateTime.now());
                    sessionRepository.save(session);
                    return ResponseEntity.ok(session);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/session/{token}/chart")
    public ResponseEntity<?> getChartData(@PathVariable String token) {
        List<TelemetryEvent> events = eventRepository.findBySessionTokenOrderByTimestampAsc(token);
        
        // Transform to lists for easy frontend Chart.js mapping
        List<String> timestamps = events.stream()
                .map(e -> e.getTimestamp().toLocalTime().toString().substring(0, 8))
                .collect(Collectors.toList());
                
        List<Integer> keystrokeCounts = events.stream()
                .map(TelemetryEvent::getKeystrokeCount)
                .collect(Collectors.toList());
                
        List<Double> wpmList = events.stream()
                .map(TelemetryEvent::getTypingSpeedWpm)
                .collect(Collectors.toList());

        Map<String, Object> chartData = new HashMap<>();
        chartData.put("timestamps", timestamps);
        chartData.put("keystrokes", keystrokeCounts);
        chartData.put("wpm", wpmList);

        return ResponseEntity.ok(chartData);
    }

    @GetMapping("/session/{token}/summary")
    public ResponseEntity<?> getSessionSummary(@PathVariable String token) {
        TelemetrySession session = sessionRepository.findBySessionToken(token).orElse(null);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }

        List<CompileEvent> compiles = compileEventRepository.findBySessionToken(token);
        long totalCompiles = compiles.size();
        long successfulCompiles = compiles.stream().filter(CompileEvent::getSuccess).count();
        long failedCompiles = totalCompiles - successfulCompiles;

        Map<String, Object> summary = new HashMap<>();
        summary.put("sessionToken", token);
        summary.put("startTime", session.getStartTime());
        summary.put("activeTimeSeconds", session.getActiveTimeSeconds());
        summary.put("totalKeystrokes", session.getTotalKeystrokes());
        summary.put("totalCompiles", totalCompiles);
        summary.put("successfulCompiles", successfulCompiles);
        summary.put("failedCompiles", failedCompiles);
        summary.put("compilationSuccessRate", totalCompiles == 0 ? 100.0 : (double) successfulCompiles / totalCompiles * 100.0);

        return ResponseEntity.ok(summary);
    }
}
