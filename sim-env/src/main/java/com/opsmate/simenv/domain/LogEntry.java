package com.opsmate.simenv.domain;

import java.time.Instant;

/**
 * A single simulated log line.
 *
 * @param timestamp when the line was emitted
 * @param level     INFO, WARN or ERROR
 * @param message   message body (may contain newlines for stack traces)
 */
public record LogEntry(Instant timestamp, String level, String message) {
}