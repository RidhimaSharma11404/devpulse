package com.devpulse.repository;

import com.devpulse.model.TelemetrySession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TelemetrySessionRepository extends JpaRepository<TelemetrySession, Long> {
    Optional<TelemetrySession> findBySessionToken(String sessionToken);
}
