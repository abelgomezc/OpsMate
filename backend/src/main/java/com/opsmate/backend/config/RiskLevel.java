package com.opsmate.backend.config;

/**
 * Risk classification for a remediation action.
 *
 * <p>Ordering from lowest to highest: {@link #LOW} &lt; {@link #MEDIUM} &lt; {@link #HIGH}.
 * Actions rated {@link #HIGH} require a recorded, validated approval before execution.</p>
 */
public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}