package com.opsmate.backend.connector.model;

import java.time.Instant;

/**
 * A single log line emitted by a monitored service.
 *
 * @param timestamp when the line was emitted
 * @param level     INFO, WARN or ERROR
 * @param message   message body
 */
public record LogEntry(Instant timestamp, String level, String message) {
}