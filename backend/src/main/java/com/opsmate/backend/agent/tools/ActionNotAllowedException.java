package com.opsmate.backend.agent.tools;

/**
 * Thrown when a requested remediation action is not permitted for the current system.
 *
 * <p>Raised by {@link ActionTools} before any side effect occurs when the action is not in
 * the system's {@code allowedActions} list, or when it fails a risk/approval gate.</p>
 */
public class ActionNotAllowedException extends RuntimeException {

    private final String action;
    private final String systemId;

    public ActionNotAllowedException(String action, String systemId, String message) {
        super(message);
        this.action = action;
        this.systemId = systemId;
    }

    public String action() {
        return action;
    }

    public String systemId() {
        return systemId;
    }
}