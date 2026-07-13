package com.devpulse.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "compile_events")
public class CompileEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sessionToken;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    private String language;

    private Boolean success;

    @Column(length = 2000)
    private String errorMessage;

    private String errorType; // e.g. "SyntaxError", "RuntimeError", "CompilationError"

    // Constructors
    public CompileEvent() {}

    public CompileEvent(String sessionToken, LocalDateTime timestamp, String language, Boolean success, String errorMessage, String errorType) {
        this.sessionToken = sessionToken;
        this.timestamp = timestamp;
        this.language = language;
        this.success = success;
        this.errorMessage = errorMessage;
        this.errorType = errorType;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
}
