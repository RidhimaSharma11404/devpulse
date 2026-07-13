package com.devpulse.repository;

import com.devpulse.model.TelemetryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TelemetryEventRepository extends JpaRepository<TelemetryEvent, Long> {
    List<TelemetryEvent> findBySessionToken(String sessionToken);
    List<TelemetryEvent> findBySessionTokenOrderByTimestampAsc(String sessionToken);
}
