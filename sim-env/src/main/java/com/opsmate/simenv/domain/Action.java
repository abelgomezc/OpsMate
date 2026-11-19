package com.opsmate.simenv.domain;

/**
 * Remediation actions that can be executed against a service.
 */
public enum Action {
    RESTART("restart"),
    RETRY_STUCK_JOBS("retry-stuck-jobs"),
    CLEAR_QUEUE("clear-queue"),
    SCALE_UP("scale-up");

    private final String slug;

    Action(String slug) {
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }

    public static Action fromSlug(String slug) {
        for (Action a : values()) {
            if (a.slug.equalsIgnoreCase(slug)) {
                return a;
            }
        }
        throw new IllegalArgumentException("Unknown action: " + slug);
    }
}