package com.devpulse.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "telemetry_sessions")
public class TelemetrySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sessionToken;

    @Column(nullable = false)
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer totalKeystrokes = 0;

    private Long activeTimeSeconds = 0L;

    // Constructors
    public TelemetrySession() {}

    public TelemetrySession(String sessionToken, LocalDateTime startTime) {
        this.sessionToken = sessionToken;
        this.startTime = startTime;
        this.totalKeystrokes = 0;
        this.activeTimeSeconds = 0L;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Integer getTotalKeystrokes() { return totalKeystrokes; }
    public void setTotalKeystrokes(Integer totalKeystrokes) { this.totalKeystrokes = totalKeystrokes; }

    public Long getActiveTimeSeconds() { return activeTimeSeconds; }
    public void setActiveTimeSeconds(Long activeTimeSeconds) { this.activeTimeSeconds = activeTimeSeconds; }
}
