package com.devpulse.repository;

import com.devpulse.model.CompileEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CompileEventRepository extends JpaRepository<CompileEvent, Long> {
    List<CompileEvent> findBySessionToken(String sessionToken);
    List<CompileEvent> findBySessionTokenOrderByTimestampAsc(String sessionToken);
}
