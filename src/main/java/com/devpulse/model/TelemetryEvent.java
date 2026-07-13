package com.devpulse.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "telemetry_events")
public class TelemetryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sessionToken;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    private Integer keystrokeCount;

    private Double typingSpeedWpm;

    private String activeFileExtension;

    private Integer intervalSeconds;

    // Constructors
    public TelemetryEvent() {}

    public TelemetryEvent(String sessionToken, LocalDateTime timestamp, Integer keystrokeCount, Double typingSpeedWpm, String activeFileExtension, Integer intervalSeconds) {
        this.sessionToken = sessionToken;
        this.timestamp = timestamp;
        this.keystrokeCount = keystrokeCount;
        this.typingSpeedWpm = typingSpeedWpm;
        this.activeFileExtension = activeFileExtension;
        this.intervalSeconds = intervalSeconds;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public Integer getKeystrokeCount() { return keystrokeCount; }
    public void setKeystrokeCount(Integer keystrokeCount) { this.keystrokeCount = keystrokeCount; }

    public Double getTypingSpeedWpm() { return typingSpeedWpm; }
    public void setTypingSpeedWpm(Double typingSpeedWpm) { this.typingSpeedWpm = typingSpeedWpm; }

    public String getActiveFileExtension() { return activeFileExtension; }
    public void setActiveFileExtension(String activeFileExtension) { this.activeFileExtension = activeFileExtension; }

    public Integer getIntervalSeconds() { return intervalSeconds; }
    public void setIntervalSeconds(Integer intervalSeconds) { this.intervalSeconds = intervalSeconds; }
}
